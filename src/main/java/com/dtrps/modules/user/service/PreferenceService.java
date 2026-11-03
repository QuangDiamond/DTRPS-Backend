package com.dtrps.modules.user.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.repository.InterestRepository;
import com.dtrps.modules.poi.repository.SpecialNeedRepository;
import com.dtrps.modules.poi.repository.TransportModeRepository;
import com.dtrps.modules.user.dto.PreferenceResponse;
import com.dtrps.modules.user.dto.UpdatePreferencesRequest;
import com.dtrps.modules.user.entity.Preference;
import com.dtrps.modules.user.repository.PreferenceRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {
  private static final String UNKNOWN_OPTION = "Lựa chọn không hợp lệ";

  private final PreferenceRepository preferenceRepository;
  private final UserRepository userRepository;
  private final InterestRepository interestRepository;
  private final TransportModeRepository transportModeRepository;
  private final SpecialNeedRepository specialNeedRepository;

  public PreferenceService(
      PreferenceRepository preferenceRepository,
      UserRepository userRepository,
      InterestRepository interestRepository,
      TransportModeRepository transportModeRepository,
      SpecialNeedRepository specialNeedRepository) {
    this.preferenceRepository = preferenceRepository;
    this.userRepository = userRepository;
    this.interestRepository = interestRepository;
    this.transportModeRepository = transportModeRepository;
    this.specialNeedRepository = specialNeedRepository;
  }

  @Transactional(readOnly = true)
  public PreferenceResponse get(Long userId) {
    return preferenceRepository
        .findByUserUserId(userId)
        .map(PreferenceResponse::from)
        .orElseGet(PreferenceResponse::empty);
  }

  @Transactional
  public PreferenceResponse update(Long userId, UpdatePreferencesRequest request) {
    Preference preference =
        preferenceRepository.findByUserUserId(userId).orElseGet(() -> newPreference(userId));
    preference.setBudgetLevel(request.budgetLevel());
    preference.setTravelStyle(request.travelStyle());
    preference.setNotes(request.notes());
    preference.setInterests(resolve(request.interests(), interestRepository::findByCodeIn));
    preference.setTransportModes(
        resolve(request.transportModes(), transportModeRepository::findByCodeIn));
    preference.setSpecialNeeds(
        resolve(request.specialNeeds(), specialNeedRepository::findByCodeIn));
    return PreferenceResponse.from(preferenceRepository.save(preference));
  }

  private Preference newPreference(Long userId) {
    Preference preference = new Preference();
    preference.setUser(userRepository.getReferenceById(userId));
    return preference;
  }

  private static <T> Set<T> resolve(Set<String> codes, Function<Set<String>, List<T>> finder) {
    Set<T> found = new HashSet<>(finder.apply(codes));
    if (found.size() != codes.size()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, UNKNOWN_OPTION);
    }
    return found;
  }
}
