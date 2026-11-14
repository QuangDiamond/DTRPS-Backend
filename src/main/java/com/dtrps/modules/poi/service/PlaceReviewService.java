package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.media.service.ImageUploadService;
import com.dtrps.modules.poi.dto.PlaceReviewDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewImage;
import com.dtrps.modules.itinerary.repository.PlanPlaceRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceReviewImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Đánh giá địa điểm: khách chấm 1-5 sao, nhận xét và tối đa {@value #MAX_PHOTOS} ảnh. Một khách chỉ
 * đánh giá một địa điểm đúng một lần (bảng {@code place_reviews} có unique trên user_id +
 * place_id) và không sửa/xóa lại được sau khi đã gửi.
 */
@Service
public class PlaceReviewService {
  private static final String ACTIVE = "ACTIVE";
  private static final String VISIBLE = "VISIBLE";
  static final int MAX_PHOTOS = 5;

  static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm";
  static final String ALREADY_REVIEWED = "Bạn đã đánh giá địa điểm này rồi";
  static final String TOO_MANY_PHOTOS = "Tối đa " + MAX_PHOTOS + " ảnh cho mỗi đánh giá";
  static final String NOT_ELIGIBLE =
      "Bạn chỉ có thể đánh giá địa điểm đã từng chọn trong một chuyến đi của mình";

  private final PlaceReviewRepository reviewRepository;
  private final PlaceReviewImageRepository imageRepository;
  private final PlaceRepository placeRepository;
  private final PlanPlaceRepository planPlaceRepository;
  private final UserRepository userRepository;
  private final ImageUploadService imageUploadService;

  public PlaceReviewService(
      PlaceReviewRepository reviewRepository,
      PlaceReviewImageRepository imageRepository,
      PlaceRepository placeRepository,
      PlanPlaceRepository planPlaceRepository,
      UserRepository userRepository,
      ImageUploadService imageUploadService) {
    this.reviewRepository = reviewRepository;
    this.imageRepository = imageRepository;
    this.placeRepository = placeRepository;
    this.planPlaceRepository = planPlaceRepository;
    this.userRepository = userRepository;
    this.imageUploadService = imageUploadService;
  }

  @Transactional(readOnly = true)
  public PageResponse<PlaceReviewDtos.ReviewResponse> list(Long placeId, Pageable pageable) {
    requirePlace(placeId);
    Page<PlaceReview> page =
        reviewRepository.findByPlacePlaceIdAndStatusOrderByCreatedAtDesc(
            placeId, VISIBLE, pageable);
    List<PlaceReviewDtos.ReviewResponse> items = withImages(page.getContent());
    return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize());
  }

  /**
   * Đánh giá đã có của khách cho địa điểm này (nếu có), kèm {@code canReview}: true nếu khách chưa
   * đánh giá và đã từng chọn địa điểm này trong một kế hoạch chuyến đi (được viết mới). Đã đánh giá
   * rồi thì không sửa/xóa lại được nữa.
   */
  @Transactional(readOnly = true)
  public PlaceReviewDtos.ReviewStatusResponse status(Long userId, Long placeId) {
    requirePlace(placeId);
    Optional<PlaceReview> existing = reviewRepository.findByPlacePlaceIdAndUserUserId(placeId, userId);
    PlaceReviewDtos.ReviewResponse response =
        existing.map(review -> toResponse(review, imageUrlsOf(review.getReviewId()))).orElse(null);
    boolean canReview =
        existing.isEmpty()
            && planPlaceRepository.existsByPlanUserUserIdAndPlacePlaceId(userId, placeId);
    return new PlaceReviewDtos.ReviewStatusResponse(canReview, response);
  }

  @Transactional
  public PlaceReviewDtos.ReviewResponse create(
      Long userId, Long placeId, Short rating, String comment, List<MultipartFile> photos) {
    Place place = requirePlace(placeId);
    if (!planPlaceRepository.existsByPlanUserUserIdAndPlacePlaceId(userId, placeId)) {
      throw new ApiException(HttpStatus.FORBIDDEN, NOT_ELIGIBLE);
    }
    if (reviewRepository.existsByUserUserIdAndPlacePlaceId(userId, placeId)) {
      throw new ApiException(HttpStatus.CONFLICT, ALREADY_REVIEWED);
    }

    List<MultipartFile> files = nonEmpty(photos);
    if (files.size() > MAX_PHOTOS) {
      throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY_PHOTOS);
    }

    PlaceReview review = new PlaceReview();
    review.setPlace(place);
    review.setUser(userRepository.getReferenceById(userId));
    review.setRating(rating);
    review.setComment(blankToNull(comment));
    review = reviewRepository.save(review);

    List<String> imageUrls = uploadImages(review, files);
    return toResponse(review, imageUrls);
  }

  // ===== Helpers =====

  private Place requirePlace(Long placeId) {
    return placeRepository
        .findById(placeId)
        .filter(place -> ACTIVE.equals(place.getStatus()))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND));
  }

  private List<String> uploadImages(PlaceReview review, List<MultipartFile> files) {
    List<String> urls = new ArrayList<>();
    for (MultipartFile file : files) {
      String url = imageUploadService.uploadReviewPhoto(file);
      PlaceReviewImage image = new PlaceReviewImage();
      image.setReview(review);
      image.setImageUrl(url);
      imageRepository.save(image);
      urls.add(url);
    }
    return urls;
  }

  private List<String> imageUrlsOf(Long reviewId) {
    return imageRepository.findByReviewReviewIdOrderByCreatedAtAsc(reviewId).stream()
        .map(PlaceReviewImage::getImageUrl)
        .toList();
  }

  /** Ảnh của mọi đánh giá trong trang, trong một truy vấn (tránh N+1). */
  private List<PlaceReviewDtos.ReviewResponse> withImages(List<PlaceReview> reviews) {
    List<Long> ids = reviews.stream().map(PlaceReview::getReviewId).toList();
    Map<Long, List<String>> imagesByReview =
        ids.isEmpty()
            ? Map.of()
            : imageRepository.findForReviews(ids).stream()
                .collect(
                    Collectors.groupingBy(
                        image -> image.getReview().getReviewId(),
                        Collectors.mapping(PlaceReviewImage::getImageUrl, Collectors.toList())));
    return reviews.stream()
        .map(review -> toResponse(review, imagesByReview.getOrDefault(review.getReviewId(), List.of())))
        .toList();
  }

  private static PlaceReviewDtos.ReviewResponse toResponse(
      PlaceReview review, List<String> imageUrls) {
    User user = review.getUser();
    return new PlaceReviewDtos.ReviewResponse(
        review.getReviewId(),
        review.getPlace().getPlaceId(),
        user.getUserId(),
        user.getFullName(),
        user.getAvatarUrl(),
        review.getRating(),
        review.getComment(),
        imageUrls,
        review.getCreatedAt(),
        review.getUpdatedAt());
  }

  private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
    if (files == null) {
      return List.of();
    }
    return files.stream().filter(file -> file != null && !file.isEmpty()).toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
