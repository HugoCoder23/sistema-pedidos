package com.portafolio.pedidos.exception;

/** Conflicto con el estado actual de un recurso (duplicados, versión desactualizada). Se responde con 409. */
public class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
