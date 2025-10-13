package com.bicentral.bicentral_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//RECONHECER HTTP 401 UNAUTHORIZED

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AutenticacaoException extends RuntimeException {
    public AutenticacaoException(String message) {
        super(message);
    }
}
