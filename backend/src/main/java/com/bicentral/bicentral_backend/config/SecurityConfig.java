package com.bicentral.bicentral_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

// Esta classe configura todas as regras de segurança da API
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Filtro JWT que futuramente validará o token em TODAS as requisições
    // Vamos criar depois — deixamos comentado por enquanto
    // private final JwtAuthFilter jwtAuthFilter;

    // public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
    //     this.jwtAuthFilter = jwtAuthFilter;
    // }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ===============================
                // 1. CONFIGURAÇÃO DE CORS
                // ===============================
                // Permite que o frontend consiga acessar o backend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ===============================
                // 2. DESABILITA CSRF
                // ===============================
                // CSRF é usado em aplicações com sessão/cookies.
                // Como usamos REST API + JWT, ele deve ser desabilitado.
                .csrf(csrf -> csrf.disable())

                // ===============================
                // 3. AUTORIZAÇÃO DE ROTAS
                // ===============================
                .authorizeHttpRequests(authorize -> authorize

                        // Rotas públicas (não exigem login)
                        .requestMatchers(
                                "/api/usuarios/cadastro",   // cadastro do usuário
                                "/api/usuarios/login",      // login
                                "/api/usuarios/verify/**",  // verificação
                                "/auth/**",                 // rotas de autenticação (login e cadastro)
                                "/api/painel"               // TODO: remover quando JWT for implementado
                        ).permitAll()

                        // Todas as outras rotas EXIGEM AUTENTICAÇÃO
                        // Isso inclui:
                        // - POST, PUT, DELETE em /api/paineis/**
                        // - GET em /api/paineis/** (incluindo /com-capa)
                        // - POST e GET em /api/painel
                        .anyRequest().authenticated()
                );

        // ================================================
        // 4. ADICIONAR FILTRO JWT ANTES DO FILTRO PADRÃO
        // ================================================
        // Esse filtro irá:
        // - ler o token
        // - validar o token
        // - liberar ou bloquear a requisição
        //
        // Só será ativado quando criarmos o JwtAuthFilter
        //
        // http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Finalmente retorna a configuração pronta
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        // Esta classe diz quais ORIGENS (sites) podem chamar sua API
        CorsConfiguration configuration = new CorsConfiguration();

        // Aqui adicionamos os frontends permitidos:
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200", // Angular
                "http://localhost:3000"  // React/Next/etc
        ));

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Todos os cabeçalhos são permitidos
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Permite envio de cookies/cabecalhos de autenticação
        configuration.setAllowCredentials(true);

        // Registra as configurações para TODAS as rotas do backend
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // Criptografador de senha usando BCrypt
    // Usado para salvar senhas de forma segura no banco
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
