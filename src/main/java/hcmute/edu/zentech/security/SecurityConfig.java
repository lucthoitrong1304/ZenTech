package hcmute.edu.zentech.security;

import hcmute.edu.zentech.security.jwt.AuthEntryPointJwt;
import hcmute.edu.zentech.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final CustomUserDetailsService customUserDetailsService;
    private final TraceIdFilter traceIdFilter;
    private final PrometheusScrapeTokenFilter prometheusScrapeTokenFilter;

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/me/**").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/internal/ai/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/payments/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/business-events").permitAll()
                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/uploads/presign").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/products/*/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/products/*/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/leave-types").authenticated()
                        .requestMatchers("/api/customers/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/api/logs/client").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/management/orders/**").hasAuthority("ORDER_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/management/orders/**").hasAuthority("ORDER_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/management/orders/**").hasAuthority("ORDER_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/management/orders/**").hasAuthority("ORDER_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/management/return-requests/**").hasAuthority("RETURN_VIEW")
                        .requestMatchers("/api/management/return-requests/**").hasAuthority("RETURN_APPROVE")
                        .requestMatchers(HttpMethod.GET, "/api/management/products/**", "/api/management/product-groups/**").hasAuthority("PRODUCT_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/management/products/**", "/api/management/product-groups/**").hasAuthority("PRODUCT_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/management/products/**", "/api/management/product-groups/**").hasAuthority("PRODUCT_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/management/products/**", "/api/management/product-groups/**").hasAuthority("PRODUCT_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/management/inventory/**").hasAuthority("INVENTORY_VIEW")
                        .requestMatchers("/api/management/inventory/**").hasAuthority("INVENTORY_UPDATE")
                        .requestMatchers(HttpMethod.GET, "/api/management/customers/**").hasAuthority("CUSTOMER_VIEW")
                        .requestMatchers("/api/management/customers/**").hasAuthority("CUSTOMER_UPDATE")
                        .requestMatchers("/api/management/leave-types/**").hasAuthority("EMPLOYEE_UPDATE")
                        .requestMatchers(HttpMethod.GET, "/api/management/employees/**").hasAuthority("EMPLOYEE_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/management/employees/**").hasAuthority("EMPLOYEE_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/management/employees/**").hasAuthority("EMPLOYEE_UPDATE")
                        .requestMatchers(HttpMethod.GET, "/api/management/coupons/**").hasAuthority("MARKETING_VIEW")
                        .requestMatchers(HttpMethod.POST, "/api/management/coupons/**").hasAuthority("MARKETING_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/management/coupons/**").hasAuthority("MARKETING_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/management/coupons/**").hasAuthority("MARKETING_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/management/reports/analyze").hasAuthority("REPORT_ANALYZE")
                        .requestMatchers(HttpMethod.POST, "/api/management/impact-analysis/incidents/*/analyze-ai").hasAuthority("REPORT_ANALYZE")
                        .requestMatchers(HttpMethod.GET, "/api/management/reports/**", "/api/management/impact-analysis/**").hasAuthority("REPORT_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/management/chat/**", "/api/management/tickets/**").hasAuthority("CHAT_VIEW")
                        .requestMatchers("/api/management/chat/**", "/api/management/tickets/**").hasAuthority("CHAT_UPDATE")
                        .requestMatchers(HttpMethod.GET, "/api/management/ai/**").hasAuthority("AI_VIEW")
                        .requestMatchers(HttpMethod.POST,
                                "/api/management/ai/demo",
                                "/api/management/ai/documents/*/reingest",
                                "/api/management/ai/products/reindex",
                                "/api/management/ai/products/verify",
                                "/api/management/ai/products/variants/*/sync",
                                "/api/management/ai/products/variants/*/verify"
                        ).hasAuthority("AI_UPDATE")
                        .requestMatchers(HttpMethod.POST, "/api/management/ai/**").hasAuthority("AI_CREATE")
                        .requestMatchers(HttpMethod.PATCH, "/api/management/ai/**").hasAuthority("AI_UPDATE")
                        .requestMatchers(HttpMethod.DELETE, "/api/management/ai/**").hasAuthority("AI_DELETE")
                        .requestMatchers(HttpMethod.GET, "/api/shifts/my-schedules").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/shifts/**", "/api/attendance/report", "/api/attendance/location-policy").hasAuthority("SCHEDULE_VIEW")
                        .requestMatchers("/api/shifts/**").hasAuthority("SCHEDULE_UPDATE")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/location-policy").hasAuthority("SCHEDULE_UPDATE")
                        .requestMatchers(HttpMethod.GET, "/api/management/leaves/**", "/api/management/attendance/adjustments/**", "/api/management/schedules/swaps/**").hasAuthority("APPROVAL_VIEW")
                        .requestMatchers("/api/management/leaves/**", "/api/management/attendance/adjustments/**", "/api/management/schedules/swaps/**").hasAuthority("APPROVAL_APPROVE")
                        .requestMatchers(HttpMethod.GET, "/api/management/pay-periods/**").hasAuthority("PAY_PERIOD_VIEW")
                        .requestMatchers("/api/management/pay-periods/**").hasAuthority("PAY_PERIOD_UPDATE")
                        .requestMatchers("/api/management/search").hasAnyAuthority(
                                "PRODUCT_VIEW",
                                "ORDER_VIEW",
                                "CUSTOMER_VIEW"
                        )

                        .requestMatchers(HttpMethod.POST, "/api/admin/activity-logs/recordings").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(prometheusScrapeTokenFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(traceIdFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Trace-Id"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOrigins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }
}
