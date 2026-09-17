package com.bicentral.bicentral_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.bicentral.bicentral_backend.job.SincronizacaoUftApplication;

// SincronizacaoUftApplication é um 2º @SpringBootApplication (ponto de entrada separado do modo
// manual de sincronização, ver job/SincronizacaoUftApplication.java) que mora dentro do mesmo
// pacote varrido por este scan. Sem excluí-lo, o Spring processa o @EnableAutoConfiguration dele
// TAMBÉM neste contexto — e os excludes dele (JPA, Hibernate, Security) se combinam com os deste
// app, desligando JPA repositories (e Security) pro sistema inteiro.
@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SincronizacaoUftApplication.class))
public class BicentralBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BicentralBackendApplication.class, args);
    }
}
