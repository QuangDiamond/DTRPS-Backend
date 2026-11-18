package com.dtrps.modules.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dtrps.modules.payment.dto.AdDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AdPackageRequestValidationTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  /** Đọc từ JSON như request thật, để kiểm tra Jackson không tự cắt phần thập phân. */
  private Set<String> invalidFields(String price, String days, String places, String order)
      throws Exception {
    String json =
        "{\"name\":\"Gói\",\"price\":%s,\"durationDays\":%s,\"maxPlaces\":%s,\"sortOrder\":%s}"
            .formatted(price, days, places, order);
    AdDtos.PackageRequest request = mapper.readValue(json, AdDtos.PackageRequest.class);
    return validator.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void acceptsWholeNumbersInRange() throws Exception {
    assertTrue(invalidFields("100000", "30", "3", "1").isEmpty());
  }

  @Test
  void rejectsDecimalSortOrder() throws Exception {
    assertEquals(Set.of("sortOrder"), invalidFields("100000", "30", "3", "1.1"));
  }

  @Test
  void rejectsDecimalsInEveryNumberField() throws Exception {
    assertEquals(
        Set.of("price", "durationDays", "maxPlaces", "sortOrder"),
        invalidFields("1000.5", "1.5", "2.5", "3.5"));
  }

  @Test
  void rejectsValuesOutOfRange() throws Exception {
    assertEquals(
        Set.of("price", "durationDays", "maxPlaces", "sortOrder"),
        invalidFields("999", "0", "101", "0"));
  }
}
