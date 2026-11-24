package com.portafolio.pedidos.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/** Construye un Pageable validando tamaño de página y campos de ordenamiento permitidos. */
final class PageRequests {

    static final int MAX_SIZE = 100;

    private PageRequests() {}

    /**
     * @param sort    formato "campo,asc|desc" (p. ej. "price,asc")
     * @param allowed alias público → propiedad JPA
     */
    static Pageable of(int page, int size, String sort, Map<String, String> allowed, Sort defaultSort) {
        if (page < 0) throw new IllegalArgumentException("page debe ser mayor o igual a 0");
        if (size < 1 || size > MAX_SIZE) throw new IllegalArgumentException("size debe estar entre 1 y " + MAX_SIZE);
        Sort s = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = allowed.get(parts[0].trim());
            if (property == null) {
                throw new IllegalArgumentException("No se puede ordenar por '" + parts[0].trim()
                        + "'. Valores permitidos: " + String.join(", ", allowed.keySet()));
            }
            Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            s = Sort.by(dir, property).and(Sort.by("id"));
        }
        return PageRequest.of(page, size, s);
    }
}
