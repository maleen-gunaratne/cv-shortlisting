package com.raketman.shortlisting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/actuator/**", "/h2-console/**")
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/favicon.ico",
                                "/error",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .requestMatchers("/h2-console/**").permitAll()

                        .requestMatchers("GET", "/api/v1/cvs/shortlisted").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/duplicates").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/search").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/{id}").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/stats").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/stats/**").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/skills").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("GET", "/api/v1/cvs/config/**").hasAnyRole("HR", "ADMIN")

                        .requestMatchers("POST", "/api/v1/cvs/process").hasAnyRole("HR", "ADMIN")
                        .requestMatchers("PUT", "/api/v1/cvs/{id}/status").hasAnyRole("HR", "ADMIN")

                        .requestMatchers("POST", "/api/v1/cvs/reprocess-duplicates").hasRole("ADMIN")
                        .requestMatchers("DELETE", "/api/v1/cvs/{id}").hasRole("ADMIN")

                        .requestMatchers("/actuator/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .httpBasic(basic -> basic
                        .realmName("CV Deduplication Tool")
                )

                // Exception handling
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setHeader("WWW-Authenticate", "Basic realm=\"CV Shortlisting\"");
                            response.sendError(401, "Authentication required");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendError(403, "Access denied");
                        })
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:3000",    // React server
                "http://localhost:4200",    // Angular server
                "http://localhost:8080"
        ));

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));

        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept",
                "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"
        ));

        configuration.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/actuator/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * In-memory user details service for demo/development
     * In production, replace with database-backed user service
     */
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("cvtool2025"))
                .roles("ADMIN", "HR")
                .build();

        UserDetails hrManager = User.builder()
                .username("hr")
                .password(passwordEncoder().encode("hr2025"))
                .roles("HR")
                .build();

        UserDetails viewer = User.builder()
                .username("viewer")
                .password(passwordEncoder().encode("viewer2025"))
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, hrManager, viewer);
    }
}
