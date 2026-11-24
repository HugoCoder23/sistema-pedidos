package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.CategoryRequest;
import com.portafolio.pedidos.dto.Requests.ProductRequest;
import com.portafolio.pedidos.dto.Requests.StockAdjustmentRequest;
import com.portafolio.pedidos.dto.Responses.CategoryResponse;
import com.portafolio.pedidos.dto.Responses.ProductResponse;
import com.portafolio.pedidos.dto.Responses.ReportResponse;
import com.portafolio.pedidos.service.CatalogService;
import com.portafolio.pedidos.service.CatalogService.ProductFilter;
import com.portafolio.pedidos.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administración: catálogo, inventario y reportes (ADMIN)")
public class AdminCatalogController {

    private final CatalogService catalog;
    private final ReportService reports;

    public AdminCatalogController(CatalogService catalog, ReportService reports) {
        this.catalog = catalog;
        this.reports = reports;
    }

    @GetMapping("/products")
    @Operation(summary = "Productos incluyendo inactivos")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String category,
                                                  @RequestParam(required = false) Boolean inStock,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String sort) {
        return catalog.search(new ProductFilter(q, category, null, null, inStock, true),
                PageRequests.of(page, size, sort, CatalogController.SORTS, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Detalle de producto (aunque esté inactivo)")
    public ProductResponse product(@PathVariable Long id) {
        return catalog.get(id, true);
    }

    @PostMapping("/products")
    @Operation(summary = "Crear producto")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse p = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + p.id())).body(p);
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Editar producto (requiere version para bloqueo optimista; no cambia el stock)")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return catalog.update(id, request);
    }

    @PatchMapping("/products/{id}/stock")
    @Operation(summary = "Ajustar inventario con una variación (+ entrada, - salida); nunca queda negativo")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return catalog.adjustStock(id, request);
    }

    @PostMapping("/categories")
    @Operation(summary = "Crear categoría")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse c = catalog.createCategory(request);
        return ResponseEntity.created(URI.create("/api/categories")).body(c);
    }

    @GetMapping("/reports/summary")
    @Operation(summary = "Ventas, pedidos por estado, productos más vendidos y stock bajo")
    public ReportResponse summary(@RequestParam(defaultValue = "30") int days) {
        return reports.summary(days);
    }
}
