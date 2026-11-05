package com.dtrps.modules.poi.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.dto.PlaceSummaryResponse;
import com.dtrps.modules.poi.entity.Favorite;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.FavoriteRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A traveller's saved places. The database has a unique constraint on (user, place), but {@link
 * #add} checks first so saving an already-saved place, or removing one twice, is never an error.
 */
@Service
public class FavoriteService {
  private static final String PLACE_NOT_FOUND = "Không tìm thấy địa điểm";

  private final FavoriteRepository favoriteRepository;
  private final PlaceRepository placeRepository;
  private final UserRepository userRepository;
  private final PlaceService placeService;

  public FavoriteService(
      FavoriteRepository favoriteRepository,
      PlaceRepository placeRepository,
      UserRepository userRepository,
      PlaceService placeService) {
    this.favoriteRepository = favoriteRepository;
    this.placeRepository = placeRepository;
    this.userRepository = userRepository;
    this.placeService = placeService;
  }

  /** Filters are optional. {@code group} is a service-group reference code. */
  @Transactional(readOnly = true)
  public PageResponse<PlaceSummaryResponse> list(
      Long userId, String keyword, String group, Pageable pageable) {
    Specification<Favorite> spec =
        (root, query, cb) -> cb.equal(root.get("user").get("userId"), userId);
    if (hasText(keyword)) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> cb.like(cb.lower(root.join("place").get("name")), like));
    }
    if (hasText(group)) {
      String code = group.trim();
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<Favorite, Place> place = root.join("place");
                Join<Place, PlaceCategory> category = place.join("category");
                return cb.equal(category.get("parent").get("code"), code);
              });
    }
    Page<Favorite> page = favoriteRepository.findAll(spec, pageable);
    List<Place> places = page.getContent().stream().map(Favorite::getPlace).toList();
    List<PlaceSummaryResponse> summaries = placeService.summaries(places);
    return new PageResponse<>(summaries, page.getTotalElements(), page.getNumber(), page.getSize());
  }

  /** Already saved: does nothing. Unknown place: 404. */
  @Transactional
  public void add(Long userId, Long placeId) {
    if (favoriteRepository.existsByUserUserIdAndPlacePlaceId(userId, placeId)) {
      return;
    }
    Place place =
        placeRepository
            .findById(placeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PLACE_NOT_FOUND));
    Favorite favorite = new Favorite();
    favorite.setUser(userRepository.getReferenceById(userId));
    favorite.setPlace(place);
    favoriteRepository.save(favorite);
  }

  /** Not saved (or already removed): does nothing. */
  @Transactional
  public void remove(Long userId, Long placeId) {
    favoriteRepository
        .findByUserUserIdAndPlacePlaceId(userId, placeId)
        .ifPresent(favoriteRepository::delete);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
