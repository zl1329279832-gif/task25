package com.procurement.config;

import com.procurement.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Supplier-specific endpoints
                .requestMatchers(HttpMethod.POST, "/api/quotations", "/api/quotations/*/submit").hasRole("SUPPLIER")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/confirm").hasRole("SUPPLIER")
                .requestMatchers(HttpMethod.POST, "/api/returns/*/confirm").hasRole("SUPPLIER")
                // Purchase manager endpoints
                .requestMatchers(HttpMethod.POST, "/api/orders/*/approve").hasRole("PURCHASE_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/comparisons/*/select").hasRole("PURCHASE_MANAGER")
                .requestMatchers("/api/approvals/pending").hasRole("PURCHASE_MANAGER")
                .requestMatchers("/api/audit-logs/**").hasRole("PURCHASE_MANAGER")
                // Warehouse endpoints
                .requestMatchers(HttpMethod.POST, "/api/deliveries", "/api/deliveries/*/receive").hasRole("WAREHOUSE")
                .requestMatchers(HttpMethod.POST, "/api/inspections").hasRole("WAREHOUSE")
                // Finance endpoints
                .requestMatchers("/api/invoices/**").hasRole("FINANCE")
                .requestMatchers("/api/reconciliations/**").hasRole("FINANCE")
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
