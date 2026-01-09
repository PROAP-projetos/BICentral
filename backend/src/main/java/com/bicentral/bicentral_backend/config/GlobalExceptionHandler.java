package com.bicentral.bicentral_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Erros de campos vazios ou inválidos
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> error = new HashMap<>();
        String mensagem = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        error.put("mensagem", mensagem);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // 2. Erros manuais (conflitos propositais)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("mensagem", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // 3. O QUE VOCÊ PRECISA: Captura a trava do SQL (uk_link_power_bi)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        String rootMsg = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();

        // Verifica se o erro no banco menciona a sua CONSTRAINT uk_link_power_bi
        if (rootMsg != null && rootMsg.contains("uk_link_power_bi")) {
            error.put("mensagem", "Você já possui este painel cadastrado na sua lista.");
            return new ResponseEntity<>(error, HttpStatus.CONFLICT); // Retorna 409
        }

        error.put("mensagem", "Não foi possível salvar devido a um conflito de dados.");
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // 4. Erros genéricos (Para o usuário não ver código técnico)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralExceptions(Exception ex) {
        logger.error("ERRO NO SERVIDOR: ", ex);
        Map<String, String> error = new HashMap<>();
        error.put("mensagem", "Ops! Tivemos uma dificuldade temporária. Por favor, tente novamente em instantes.");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}