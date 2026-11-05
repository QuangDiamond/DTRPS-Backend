
package com.dtrps.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.util.UrlPathHelper;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        AuthProperties.class,
        BootstrapAdminProperties.class
})
public class SecurityConfig {
    private static final Set<String> PUBLIC_AUTH_PATHS =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/refresh",
                    "/api/auth/revoke",
                    // Sign-up and password reset with an emailed code
                    "/api/auth/register/otp",
                    "/api/auth/register/verify",
                    "/api/auth/register/complete",
                    "/api/auth/password/otp",
                    "/api/auth/password/verify",
                    "/api/auth/password/reset",
                    // Continue with Google (travellers and partners)
                    "/api/auth/google");

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();
    private static final int BCRYPT_STRENGTH = 12;

    /** Compares decoded paths for public auth endpoints. */
    public static boolean isPublicAuthRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && PUBLIC_AUTH_PATHS.contains(decodedPath(request));
    }

    public static String decodedPath(HttpServletRequest request) {
        return PATH_HELPER.getPathWithinApplication(request);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthErrorHandler errorHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            BearerTokenResolver bearerTokenResolver,
            AuthProperties authProperties,
            ObjectMapper objectMapper)
            throws Exception {

        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests ->
                                requests
                                        // Event streams (SSE) finish on an async dispatch; the
                                        // request was already authenticated when it started.
                                        .dispatcherTypeMatchers(DispatcherType.ASYNC)
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.POST,
                                                PUBLIC_AUTH_PATHS.toArray(String[]::new))
                                        .permitAll()

                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/health",
                                                "/swagger-ui.html",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**")
                                        .permitAll()

                                        // Admin APIs always require ADMIN role.
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")

                                        // Public read-only APIs.
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/reference/**",
                                                "/api/places",
                                                "/api/places/{id}")
                                        .permitAll()

                                        .requestMatchers("/error")
                                        .permitAll()

                                        // Other endpoints still require authentication.
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(
                        server ->
                                server
                                        .bearerTokenResolver(bearerTokenResolver)
                                        .authenticationEntryPoint(errorHandler)
                                        .accessDeniedHandler(errorHandler)
                                        .jwt(
                                                jwt -> jwt.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(errorHandler)
                                        .accessDeniedHandler(errorHandler))
                .addFilterBefore(
                        new AuthRateLimitFilter(
                                authProperties.rateLimitPerMinute(),
                                objectMapper),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    /** Public authentication calls can work with expired access tokens. */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

        return request ->
                isPublicAuthRequest(request)
                        ? null
                        : delegate.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
