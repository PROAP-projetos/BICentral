package com.bicentral.bicentral_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward rotas da SPA (Angular) para index.html
        // Isso garante que rotas como /aceitar-convite sejam servidas pelo Angular
        registry.addViewController("/aceitar-convite")
                .setViewName("forward:/index.html");
        
        // Alternativa: redirecionar todas as rotas não-API para index.html
        // Isso é útil para outras rotas da SPA também
        registry.addViewController("/{spring:(?!api|static|error).*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{spring:(?!api|static|error).*}/**")
                .setViewName("forward:/index.html");
    }
}
