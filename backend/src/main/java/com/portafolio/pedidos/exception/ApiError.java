package com.portafolio.pedidos.exception;

import java.time.Instant;
import java.util.List;

/** Formato único para todas las respuestas de error de la API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}
}
