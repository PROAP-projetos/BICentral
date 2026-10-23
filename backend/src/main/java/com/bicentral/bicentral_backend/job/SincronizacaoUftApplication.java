package com.bicentral.bicentral_backend.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

// Ponto de entrada SEPARADO do app principal (BicentralBackendApplication), só pro modo manual
// de sincronização (ver SincronizacaoUftRunner). Usar o @SpringBootApplication principal varreria
// TODOS os beans do sistema — segurança, IA, todos os services de admin etc. — e vários deles
// fazem "CREATE TABLE IF NOT EXISTS" direto no CONSTRUTOR, ou seja, tentam conexão de banco só
// pra subir o contexto, mesmo numa fase (ex: "fetch") que não usa banco nenhum. Essa classe só
// escaneia o pacote "job", então só cria os 3 beans que a sincronização realmente precisa.
@SpringBootApplication(exclude = { HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class, SecurityAutoConfiguration.class })
@ComponentScan(basePackageClasses = { SincronizacaoPatJob.class })
public class SincronizacaoUftApplication {
    public static void main(String[] args) {
        SpringApplication.run(SincronizacaoUftApplication.class, args);
    }
}
