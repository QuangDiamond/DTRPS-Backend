package com.dtrps;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiDocsTest extends ApiTestBase {
  @Test
  @DisplayName("the OpenAPI document is public and describes the routes and JWT scheme")
  void apiDocs_describeRoutesAndBearerScheme() throws Exception {
    getAs("/v3/api-docs", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
        .andExpect(jsonPath("$.paths['/api/admin/users'].get").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
  }

  @Test
  @DisplayName("Swagger UI is public")
  void swaggerUi_isPublic() throws Exception {
    getAs("/swagger-ui/index.html", null).andExpect(status().isOk());
  }
}
