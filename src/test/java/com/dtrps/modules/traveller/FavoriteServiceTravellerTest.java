
package com.dtrps.modules.traveller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.dto.PlaceSummaryResponse;
import com.dtrps.modules.poi.entity.Favorite;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.FavoriteRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.service.FavoriteService;
import com.dtrps.modules.poi.service.PlaceService;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTravellerTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaceService placeService;

    private FavoriteService service;

    @BeforeEach
    void setUp() {
        service = new FavoriteService(
                favoriteRepository,
                placeRepository,
                userRepository,
                placeService
        );
    }

    // Tao dia diem gia de test
    private Place testPlace() {
        Place place = new Place();
        place.setPlaceId(21L);
        place.setName("Ho Guom");
        return place;
    }

    // UTC013: Them dia diem yeu thich thanh cong
    @Test
    @DisplayName("UTC013 - Add favorite successfully")
    void utc013_addFavorite_success() {

        User user = new User();
        user.setUserId(10L);

        Place place = testPlace();

        when(favoriteRepository
                .existsByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(false);

        when(placeRepository.findById(21L))
                .thenReturn(Optional.of(place));

        when(userRepository.getReferenceById(10L))
                .thenReturn(user);

        service.add(10L, 21L);

        ArgumentCaptor<Favorite> captor =
                ArgumentCaptor.forClass(Favorite.class);

        verify(favoriteRepository)
                .save(captor.capture());

        Favorite savedFavorite = captor.getValue();

        assertThat(savedFavorite.getUser())
                .isSameAs(user);

        assertThat(savedFavorite.getPlace())
                .isSameAs(place);
    }

    // UTC014: Dia diem da co trong danh sach yeu thich
    @Test
    @DisplayName("UTC014 - Favorite already exists")
    void utc014_addFavorite_duplicate() {

        when(favoriteRepository
                .existsByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(true);

        service.add(10L, 21L);

        verify(favoriteRepository, never())
                .save(any(Favorite.class));

        verify(placeRepository, never())
                .findById(21L);
    }

    // UTC015: Them dia diem khong ton tai
    @Test
    @DisplayName("UTC015 - Place not found")
    void utc015_addFavorite_notFound() {

        when(placeRepository.findById(99999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.add(10L, 99999L)
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.NOT_FOUND)
                );

        verify(favoriteRepository, never())
                .save(any(Favorite.class));
    }

    // UTC016: Xoa dia diem yeu thich thanh cong
    @Test
    @DisplayName("UTC016 - Remove favorite successfully")
    void utc016_removeFavorite_success() {

        Favorite favorite = new Favorite();

        when(favoriteRepository
                .findByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(Optional.of(favorite));

        service.remove(10L, 21L);

        verify(favoriteRepository)
                .delete(favorite);
    }

    // UTC017: Xoa dia diem chua co trong danh sach
    @Test
    @DisplayName("UTC017 - Favorite does not exist")
    void utc017_removeFavorite_notFound() {

        when(favoriteRepository
                .findByUserUserIdAndPlacePlaceId(10L, 21L))
                .thenReturn(Optional.empty());

        service.remove(10L, 21L);

        verify(favoriteRepository, never())
                .delete(any(Favorite.class));
    }

    // UTC018: Lay danh sach dia diem yeu thich
    @Test
    @DisplayName("UTC018 - Get favorite list")
    void utc018_getFavorites_success() {

        Pageable pageable = PageRequest.of(0, 10);

        Favorite favorite = new Favorite();
        favorite.setPlace(testPlace());

        when(favoriteRepository.findAll(
                any(Specification.class),
                eq(pageable)
        )).thenReturn(
                new PageImpl<>(
                        List.of(favorite),
                        pageable,
                        1
                )
        );

        PlaceSummaryResponse summary =
                new PlaceSummaryResponse(
                        21L,
                        "Ho Guom",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        0
                );

        when(placeService.summaries(
                List.of(favorite.getPlace())
        )).thenReturn(List.of(summary));

        PageResponse<PlaceSummaryResponse> result =
                service.list(
                        10L,
                        null,
                        null,
                        pageable
                );

        assertThat(result.total())
                .isEqualTo(1);

        assertThat(result.page())
                .isEqualTo(0);

        assertThat(result.size())
                .isEqualTo(10);

        assertThat(result.items())
                .hasSize(1);

        assertThat(result.items().getFirst().id())
                .isEqualTo(21L);
    }
}
