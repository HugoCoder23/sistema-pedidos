package com.portafolio.pedidos.exception;

/**
 * La petición es válida en forma pero viola una regla de negocio
 * (sin stock, carrito vacío, transición de estado inválida...). Se responde con 422.
 */
public class BusinessRuleException extends RuntimeException {
    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
