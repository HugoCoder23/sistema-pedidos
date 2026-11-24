package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Responses.CategoryResponse;
import com.portafolio.pedidos.dto.Responses.ProductResponse;
import com.portafolio.pedidos.service.CatalogService;
import com.portafolio.pedidos.service.CatalogService.ProductFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Catálogo (público)")
@SecurityRequirements
public class CatalogController {

    static final Map<String, String> SORTS = Map.of(
            "name", "name", "price", "price", "newest", "createdAt", "stock", "stock", "sku", "sku");

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    @Operation(summary = "Catálogo con búsqueda, filtros y paginación (solo productos activos)")
    public PageResponse<ProductResponse> products(
            @Parameter(description = "Texto en nombre, SKU o descripción") @RequestParam(required = false) String q,
            @Parameter(description = "Slug de la categoría") @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @Parameter(description = "name | price | newest, con ,asc o ,desc") @RequestParam(required = false) String sort) {
        return catalog.search(new ProductFilter(q, category, minPrice, maxPrice, inStock, false),
                PageRequests.of(page, size, sort, SORTS, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Detalle de un producto activo")
    public ProductResponse product(@PathVariable Long id) {
        return catalog.get(id, false);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categorías con número de productos activos")
    public List<CategoryResponse> categories() {
        return catalog.categories();
    }
}
