
package com.dtrps.modules.traveller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.SpecialNeed;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.SpecialNeedRepository;
import com.dtrps.modules.user.dto.PreferenceResponse;
import com.dtrps.modules.user.dto.UpdatePreferencesRequest;
import com.dtrps.modules.user.entity.Preference;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.PreferenceRepository;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.PreferenceService;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTravellerTest {

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlaceCategoryRepository categoryRepository;

    @Mock
    private SpecialNeedRepository specialNeedRepository;

    private PreferenceService service;

    @BeforeEach
    void setUp() {
        service = new PreferenceService(
                preferenceRepository,
                userRepository,
                categoryRepository,
                specialNeedRepository
        );
    }

    // Tao danh muc gia de test
    private PlaceCategory category(String code) {
        PlaceCategory category = new PlaceCategory();
        category.setCode(code);
        return category;
    }

    // Tao preference gia cho user ID 10
    private Preference existingPreference() {
        User user = new User();
        user.setUserId(10L);

        Preference preference = new Preference();
        preference.setUser(user);

        return preference;
    }

    // UTC007: Lay so thich da luu
    @Test
    @DisplayName("UTC007 - Get saved preferences")
    void utc007_getPreferences_success() {

        Preference preference = existingPreference();

        preference.setNotes("Quiet trip");

        preference.setInterests(
                Set.of(category("nature"))
        );

        SpecialNeed need = new SpecialNeed();
        need.setCode("wheelchair");

        preference.setSpecialNeeds(Set.of(need));

        when(preferenceRepository.findByUserUserId(10L))
                .thenReturn(Optional.of(preference));

        PreferenceResponse result = service.get(10L);

        assertThat(result.notes())
                .isEqualTo("Quiet trip");

        assertThat(result.interests())
                .contains("nature");

        assertThat(result.specialNeeds())
                .contains("wheelchair");

        verify(preferenceRepository)
                .findByUserUserId(10L);
    }

    // UTC008: User chua co so thich
    @Test
    @DisplayName("UTC008 - Get empty preferences")
    void utc008_getPreferences_empty() {

        when(preferenceRepository.findByUserUserId(11L))
                .thenReturn(Optional.empty());

        PreferenceResponse result = service.get(11L);

        assertThat(result.notes())
                .isNull();

        assertThat(result.interests())
                .isEmpty();

        assertThat(result.specialNeeds())
                .isEmpty();

        assertThat(result.experiencePreferences())
                .isEmpty();
    }

    // UTC009: Tao so thich moi thanh cong
    @Test
    @DisplayName("UTC009 - Create new preferences")
    void utc009_updatePreferences_new() {

        User user = new User();
        user.setUserId(11L);

        when(preferenceRepository.findByUserUserId(11L))
                .thenReturn(Optional.empty());

        when(userRepository.getReferenceById(11L))
                .thenReturn(user);

        when(categoryRepository.findByCodeIn(
                Set.of("nature")
        )).thenReturn(
                List.of(category("nature"))
        );

        when(specialNeedRepository.findByCodeIn(
                Set.of()
        )).thenReturn(List.of());

        when(preferenceRepository.save(
                any(Preference.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        UpdatePreferencesRequest request =
                new UpdatePreferencesRequest(
                        "Quiet trip",
                        Set.of("nature"),
                        Set.of(),
                        List.of()
                );

        PreferenceResponse result = service.update(
                11L,
                request
        );

        assertThat(result.notes())
                .isEqualTo("Quiet trip");

        assertThat(result.interests())
                .contains("nature");

        verify(preferenceRepository)
                .save(any(Preference.class));
    }

    // UTC010: Cap nhat so thich da ton tai
    @Test
    @DisplayName("UTC010 - Update existing preferences")
    void utc010_updatePreferences_existing() {

        Preference preference = existingPreference();

        when(preferenceRepository.findByUserUserId(10L))
                .thenReturn(Optional.of(preference));

        when(categoryRepository.findByCodeIn(
                Set.of("food")
        )).thenReturn(
                List.of(category("food"))
        );

        when(specialNeedRepository.findByCodeIn(
                Set.of()
        )).thenReturn(List.of());

        when(preferenceRepository.save(preference))
                .thenReturn(preference);

        UpdatePreferencesRequest request =
                new UpdatePreferencesRequest(
                        "New note",
                        Set.of("food"),
                        Set.of(),
                        List.of()
                );

        PreferenceResponse result = service.update(
                10L,
                request
        );

        assertThat(result.notes())
                .isEqualTo("New note");

        assertThat(result.interests())
                .contains("food");

        verify(preferenceRepository)
                .save(preference);

        verify(userRepository, never())
                .getReferenceById(any());
    }

    // UTC011: Nhap ma danh muc khong hop le
    @Test
    @DisplayName("UTC011 - Invalid category")
    void utc011_updatePreferences_invalidCategory() {

        Preference preference = existingPreference();

        when(preferenceRepository.findByUserUserId(10L))
                .thenReturn(Optional.of(preference));

        when(categoryRepository.findByCodeIn(
                Set.of("wrong_code")
        )).thenReturn(List.of());

        UpdatePreferencesRequest request =
                new UpdatePreferencesRequest(
                        null,
                        Set.of("wrong_code"),
                        Set.of(),
                        List.of()
                );

        assertThatThrownBy(
                () -> service.update(10L, request)
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.BAD_REQUEST)
                );

        verify(preferenceRepository, never())
                .save(any(Preference.class));
    }

    // UTC012: Nhap ma trai nghiem khong hop le
    @Test
    @DisplayName("UTC012 - Invalid experience preference")
    void utc012_updatePreferences_invalidExperience() {

        Preference preference = existingPreference();

        when(preferenceRepository.findByUserUserId(10L))
                .thenReturn(Optional.of(preference));

        when(categoryRepository.findByCodeIn(
                Set.of()
        )).thenReturn(List.of());

        when(specialNeedRepository.findByCodeIn(
                Set.of()
        )).thenReturn(List.of());

        UpdatePreferencesRequest request =
                new UpdatePreferencesRequest(
                        null,
                        Set.of(),
                        Set.of(),
                        List.of("unknown_option")
                );

        assertThatThrownBy(
                () -> service.update(10L, request)
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.BAD_REQUEST)
                );

        verify(preferenceRepository, never())
                .save(any(Preference.class));
    }
}
