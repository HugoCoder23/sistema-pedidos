package com.portafolio.pedidos.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/** El número no puede ser cero (p. ej. un ajuste de inventario sin variación). */
@Documented
@Constraint(validatedBy = NonZeroValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonZero {
    String message() default "La variación no puede ser 0";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
