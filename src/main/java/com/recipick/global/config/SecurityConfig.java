package com.recipick.global.config;

import com.recipick.global.security.AdminStatusChecker;
import com.recipick.global.security.AuthCsrfMatcher;
import com.recipick.global.security.AuthCsrfTokenRequestHandler;
import com.recipick.global.security.JwtAccessDeniedHandler;
import com.recipick.global.security.JwtAuthenticationEntryPoint;
import com.recipick.global.security.JwtAuthenticationFilter;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.MemberStatusChecker;
import com.recipick.global.security.SecurityErrorResponseWriter;
import com.recipick.global.security.SecurityRequestMatchers;
import com.recipick.global.security.SessionStatusChecker;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({LoginProperties.class, CorsProperties.class})
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider tokenProvider,
            SecurityErrorResponseWriter responseWriter, MemberStatusChecker memberStatusChecker,
            AdminStatusChecker adminStatusChecker, SessionStatusChecker sessionStatusChecker,
            CsrfTokenRepository csrfTokenRepository,
            JwtAuthenticationEntryPoint entryPoint,
            JwtAccessDeniedHandler deniedHandler, CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new AuthCsrfTokenRequestHandler())
                        .requireCsrfProtectionMatcher(new AuthCsrfMatcher()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, exception) -> {
                            if (exception instanceof org.springframework.security.web.csrf.CsrfException) {
                                responseWriter.write(response, com.recipick.global.exception.AuthErrorCode.INVALID_CSRF);
                            } else {
                                deniedHandler.handle(request, response, exception);
                            }
                        }))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, responseWriter, memberStatusChecker,
                        adminStatusChecker, sessionStatusChecker),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.to("health", "prometheus")).permitAll()
                        .requestMatchers("/api/docs", "/api/docs/**", "/api/swagger-ui", "/api/swagger-ui/**")
                        .permitAll()
                        .requestMatchers(SecurityRequestMatchers.PUBLIC).permitAll()
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "MART_ADMIN")
                        .requestMatchers("/api/**").hasRole("MEMBER")
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-CSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository(LoginProperties properties) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieName("authCsrfToken");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(false)
                .secure(properties.refreshCookieSecure()).sameSite("Strict"));
        return repository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
