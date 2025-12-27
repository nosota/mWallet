package com.nosota.mwallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> {
                    List<String> profiles = Arrays.asList(environment.getActiveProfiles());
                    if (!profiles.contains("dev")) {
                        auth.requestMatchers("/v3/api-docs/**").denyAll();
                        auth.requestMatchers("/swagger-ui/**").denyAll();
                    }
                    auth.anyRequest().permitAll();
                })
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}