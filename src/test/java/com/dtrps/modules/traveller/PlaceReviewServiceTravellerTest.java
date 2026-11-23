
package com.dtrps.modules.traveller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.repository.ActivityProgressRepository;
import com.dtrps.modules.media.service.ImageUploadService;
import com.dtrps.modules.poi.dto.PlaceReviewDtos;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceReview;
import com.dtrps.modules.poi.entity.PlaceReviewImage;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.PlaceReviewImageRepository;
import com.dtrps.modules.poi.repository.PlaceReviewRepository;
import com.dtrps.modules.poi.service.AdminReviewService;
import com.dtrps.modules.poi.service.PlaceReviewService;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class PlaceReviewServiceTravellerTest {

    @Mock
    private PlaceReviewRepository reviewRepository;

    @Mock
    private PlaceReviewImageRepository imageRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private ActivityProgressRepository activityProgressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ImageUploadService imageUploadService;

    @Mock
    private AdminReviewService adminReviews;

    private PlaceReviewService service;

    @BeforeEach
    void setUp() {
        service = new PlaceReviewService(
                reviewRepository,
                imageRepository,
                placeRepository,
                activityProgressRepository,
                userRepository,
                imageUploadService,
                adminReviews
        );
    }

    // Tao dia diem gia de test
    private Place activePlace() {
        Place place = new Place();
        place.setPlaceId(21L);
        place.setStatus("ACTIVE");
        place.setName("Ho Guom");
        return place;
    }

    // Tao user gia de test
    private User testUser() {
        User user = new User();
        user.setUserId(10L);
        user.setFullName("Nguyen A");
        return user;
    }

    // UTC019: User da di va duoc phep danh gia
    @Test
    @DisplayName("UTC019 - User can review place")
    void utc019_reviewStatus_allowed() {

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(activePlace()));

        when(activityProgressRepository
                .countCompletedVisits(10L, 21L))
                .thenReturn(1L);

        when(reviewRepository
                .countByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(0L);

        PlaceReviewDtos.ReviewStatusResponse result =
                service.status(10L, 21L);

        assertThat(result.canReview()).isTrue();

        verify(placeRepository).findById(21L);
    }

    // UTC020: User da danh gia het so lan duoc phep
    @Test
    @DisplayName("UTC020 - User cannot review again")
    void utc020_reviewStatus_notAllowed() {

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(activePlace()));

        when(activityProgressRepository
                .countCompletedVisits(10L, 21L))
                .thenReturn(1L);

        when(reviewRepository
                .countByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(1L);

        PlaceReviewDtos.ReviewStatusResponse result =
                service.status(10L, 21L);

        assertThat(result.canReview()).isFalse();
    }

    // UTC021: Tao danh gia thanh cong
    @Test
    @DisplayName("UTC021 - Create review successfully")
    void utc021_createReview_success() {

        Place place = activePlace();
        User user = testUser();

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(place));

        when(activityProgressRepository
                .countCompletedVisits(10L, 21L))
                .thenReturn(1L);

        when(reviewRepository
                .countByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(0L);

        when(userRepository.getReferenceById(10L))
                .thenReturn(user);

        when(reviewRepository.save(any(PlaceReview.class)))
                .thenAnswer(invocation -> {
                    PlaceReview review =
                            invocation.getArgument(0);
                    review.setReviewId(61L);
                    return review;
                });

        PlaceReviewDtos.ReviewResponse result =
                service.create(
                        10L,
                        21L,
                        (short) 5,
                        "Good place",
                        List.of()
                );

        assertThat(result.reviewId())
                .isEqualTo(61L);

        assertThat(result.userId())
                .isEqualTo(10L);

        assertThat(result.placeId())
                .isEqualTo(21L);

        assertThat(result.rating())
                .isEqualTo((short) 5);

        assertThat(result.comment())
                .isEqualTo("Good place");

        assertThat(result.imageUrls())
                .isEmpty();

        verify(reviewRepository)
                .save(any(PlaceReview.class));

        verify(adminReviews)
                .placeReviewCreated(any(PlaceReview.class));
    }

    // UTC022: User chua di dia diem nen khong duoc danh gia
    @Test
    @DisplayName("UTC022 - User is not eligible to review")
    void utc022_createReview_notEligible() {

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(activePlace()));

        when(activityProgressRepository
                .countCompletedVisits(10L, 21L))
                .thenReturn(0L);

        when(reviewRepository
                .countByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(0L);

        assertThatThrownBy(
                () -> service.create(
                        10L,
                        21L,
                        (short) 4,
                        "Nice place",
                        List.of()
                )
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.FORBIDDEN)
                );

        verify(reviewRepository, never())
                .save(any(PlaceReview.class));
    }

    // UTC023: Dia diem khong ton tai
    @Test
    @DisplayName("UTC023 - Place does not exist")
    void utc023_createReview_notFound() {

        when(placeRepository.findById(99999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.create(
                        10L,
                        99999L,
                        (short) 4,
                        "Nice place",
                        List.of()
                )
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.NOT_FOUND)
                );

        verify(reviewRepository, never())
                .save(any(PlaceReview.class));
    }

    // UTC024: Vuot qua gioi han 5 anh danh gia
    @Test
    @DisplayName("UTC024 - Too many review photos")
    void utc024_createReview_tooManyPhotos() {

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(activePlace()));

        when(activityProgressRepository
                .countCompletedVisits(10L, 21L))
                .thenReturn(1L);

        when(reviewRepository
                .countByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(0L);

        List<MultipartFile> photos = new ArrayList<>();

        // Tao 6 anh gia (backend chi cho toi da 5)
        for (int i = 0; i < 6; i++) {
            MultipartFile file =
                    org.mockito.Mockito.mock(MultipartFile.class);

            when(file.isEmpty()).thenReturn(false);

            photos.add(file);
        }

        assertThatThrownBy(
                () -> service.create(
                        10L,
                        21L,
                        (short) 5,
                        "Beautiful place",
                        photos
                )
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.BAD_REQUEST)
                );

        verify(reviewRepository, never())
                .save(any(PlaceReview.class));

        verify(imageUploadService, never())
                .uploadReviewPhoto(any(MultipartFile.class));
    }

    // UTC025: Lay danh sach danh gia cua dia diem
    @Test
    @DisplayName("UTC025 - Get visible reviews with images")
    void utc025_getReviews_success() {

        Pageable pageable = PageRequest.of(0, 10);

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(activePlace()));

        PlaceReview review = new PlaceReview();
        review.setReviewId(61L);
        review.setPlace(activePlace());
        review.setUser(testUser());
        review.setRating((short) 5);
        review.setComment("Very nice");
        review.setStatus("VISIBLE");

        when(reviewRepository
                .findByPlacePlaceIdAndStatusOrderByCreatedAtDesc(
                        21L,
                        "VISIBLE",
                        pageable
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(review),
                        pageable,
                        1
                )
        );

        PlaceReviewImage image = new PlaceReviewImage();
        image.setReview(review);
        image.setImageUrl(
                "https://example.test/review.jpg"
        );

        when(imageRepository.findForReviews(List.of(61L)))
                .thenReturn(List.of(image));

        PageResponse<PlaceReviewDtos.ReviewResponse> result =
                service.list(21L, pageable);

        assertThat(result.total())
                .isEqualTo(1);

        assertThat(result.page())
                .isEqualTo(0);

        assertThat(result.size())
                .isEqualTo(10);

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.items().getFirst().reviewId())
                .isEqualTo(61L);

        assertThat(result.items().getFirst().imageUrls())
                .containsExactly(
                        "https://example.test/review.jpg"
                );
    }
}
