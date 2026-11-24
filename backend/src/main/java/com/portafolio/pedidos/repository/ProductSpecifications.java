package com.portafolio.pedidos.repository;

import com.portafolio.pedidos.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/** Filtros combinables del catálogo. Cada método devuelve null si el filtro no aplica. */
public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> activeOnly(boolean activeOnly) {
        return activeOnly ? (root, q, cb) -> cb.isTrue(root.get("active")) : null;
    }

    public static Specification<Product> text(String text) {
        if (text == null || text.isBlank()) return null;
        String like = "%" + text.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like, '\\'),
                cb.like(cb.lower(root.get("sku")), like, '\\'),
                cb.like(cb.lower(root.get("description")), like, '\\'));
    }

    public static Specification<Product> category(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("category").get("slug"), slug.trim());
    }

    public static Specification<Product> minPrice(BigDecimal min) {
        return min == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> maxPrice(BigDecimal max) {
        return max == null ? null : (root, q, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> inStock(Boolean inStock) {
        return Boolean.TRUE.equals(inStock) ? (root, q, cb) -> cb.greaterThan(root.get("stock"), 0) : null;
    }
}
