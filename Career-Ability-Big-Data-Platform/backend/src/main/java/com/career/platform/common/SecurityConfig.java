package com.career.platform.common;

import com.career.platform.auth.security.JwtAuthenticationFilter;
import com.career.platform.auth.security.JwtProperties;
import com.career.platform.auth.security.JwtTokenProvider;
import com.career.platform.auth.bootstrap.BootstrapAdminProperties;
import com.career.platform.auth.security.PlatformUserDetailsService;
import com.career.platform.auth.security.RestAccessDeniedHandler;
import com.career.platform.auth.security.RestAuthenticationEntryPoint;
import com.career.platform.auth.security.TokenStore;
import com.career.platform.common.security.BusinessRateLimitFilter;
import com.career.platform.common.security.RequestCorrelationFilter;
import com.career.platform.common.security.SecurityResponseHeaderFilter;
import com.career.platform.openapi.filter.ApiKeyAuthenticationFilter;
import com.career.platform.openapi.ratelimit.ApiRateLimiter;
import com.career.platform.openapi.service.ApiCallLogService;
import com.career.platform.openapi.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, BootstrapAdminProperties.class})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider tokenProvider,
            TokenStore tokenStore,
            PlatformUserDetailsService userDetailsService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource,
            ApiKeyService apiKeyService,
            ApiRateLimiter rateLimiter,
            ApiCallLogService callLogService,
            ObjectMapper objectMapper,
            @Value("${security.rate-limit.enabled:true}") boolean businessRateLimitEnabled,
            @Value("${security.rate-limit.requests-per-minute:600}") int businessRequestsPerMinute) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(
                tokenProvider,
                tokenStore,
                userDetailsService,
                authenticationEntryPoint);
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                rateLimiter,
                callLogService,
                objectMapper);
        RequestCorrelationFilter requestCorrelationFilter = new RequestCorrelationFilter();
        SecurityResponseHeaderFilter securityResponseHeaderFilter = new SecurityResponseHeaderFilter();
        BusinessRateLimitFilter businessRateLimitFilter = new BusinessRateLimitFilter(
                objectMapper,
                businessRateLimitEnabled,
                businessRequestsPerMinute);
        http.headers().frameOptions().sameOrigin();
        http.csrf().disable()
                .cors().configurationSource(corsConfigurationSource)
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/health/**")
                .permitAll()
                .anyRequest()
                .authenticated();
        // Request context is available to all security failures and the JSON log appender.
        http.addFilterBefore(requestCorrelationFilter, SecurityContextPersistenceFilter.class);
        http.addFilterAfter(securityResponseHeaderFilter, HeaderWriterFilter.class);
        http.addFilterAfter(businessRateLimitFilter, RequestCorrelationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // Open endpoints require both credentials. JWT must be established before binding it to the API key owner.
        http.addFilterAfter(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toList());
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException("security.cors.allowed-origins must contain explicit origins only");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-API-Key", RequestCorrelationFilter.REQUEST_ID_HEADER));
        configuration.setExposedHeaders(Arrays.asList(
                "Content-Disposition", "X-RateLimit-Limit", "X-RateLimit-Remaining",
                "X-Business-RateLimit-Limit", "X-Business-RateLimit-Remaining",
                RequestCorrelationFilter.REQUEST_ID_HEADER));
        // The application uses bearer headers rather than browser cookies.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
