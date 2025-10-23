package com.bicentral.bicentral_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Desativa CSRF (comum para APIs stateless)
                .csrf(csrf -> csrf.disable())
                // Configuração de CORS (importante para Angular/Spring na mesma máquina)
                .cors(cors -> cors.disable()) // Desativa para simplificar o teste local
                
                .authorizeHttpRequests(auth -> auth
                        // Permite acesso não autenticado aos endpoints de usuários
                        .requestMatchers("/api/usuarios/**").permitAll()
                        
                        // PERMITE ACESSO NÃO AUTENTICADO aos endpoints de painéis
                        .requestMatchers("/api/paineis/**").permitAll()
                        
                        // Qualquer outra requisição deve ser autenticada
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}