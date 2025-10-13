package com.bicentral.bicentral_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//RECONHECER HTTP 409 CONFLITO

@ResponseStatus(HttpStatus.CONFLICT)
public class RecursoJaExistenteException extends RuntimeException {
    public RecursoJaExistenteException(String message) {
        super(message);
    }
}
