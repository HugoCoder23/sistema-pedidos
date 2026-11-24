package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.CategoryRequest;
import com.portafolio.pedidos.dto.Requests.ProductRequest;
import com.portafolio.pedidos.dto.Requests.StockAdjustmentRequest;
import com.portafolio.pedidos.dto.Responses.CategoryResponse;
import com.portafolio.pedidos.dto.Responses.ProductResponse;
import com.portafolio.pedidos.entity.Category;
import com.portafolio.pedidos.entity.Product;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.exception.ConflictException;
import com.portafolio.pedidos.exception.ResourceNotFoundException;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.CategoryRepository;
import com.portafolio.pedidos.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.portafolio.pedidos.repository.ProductSpecifications;
import static com.portafolio.pedidos.repository.ProductSpecifications.*;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final OrderMapper mapper;
    private final EntityManager entityManager;

    public CatalogService(ProductRepository products, CategoryRepository categories, OrderMapper mapper,
                          EntityManager entityManager) {
        this.products = products;
        this.categories = categories;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    public record ProductFilter(String text, String category, BigDecimal minPrice, BigDecimal maxPrice,
                                Boolean inStock, boolean includeInactive) {}

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(ProductFilter f, Pageable pageable) {
        if (f.minPrice() != null && f.maxPrice() != null && f.minPrice().compareTo(f.maxPrice()) > 0) {
            throw new IllegalArgumentException("minPrice no puede ser mayor que maxPrice");
        }
        Specification<Product> spec = Specification.where(activeOnly(!f.includeInactive()))
                .and(text(f.text()))
                .and(ProductSpecifications.category(f.category()))
                .and(minPrice(f.minPrice()))
                .and(maxPrice(f.maxPrice()))
                .and(inStock(f.inStock()));
        return PageResponse.of(products.findAll(spec, pageable), mapper::toResponse);
    }

    /** Detalle público: un producto inactivo no existe para el cliente. */
    @Transactional(readOnly = true)
    public ProductResponse get(Long id, boolean includeInactive) {
        Product p = products.findWithCategoryById(id)
                .filter(x -> includeInactive || x.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        Map<Long, Long> counts = new HashMap<>();
        categories.countActiveProductsByCategory().forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        String slug = slugify(name);
        if (categories.existsByNameIgnoreCase(name) || categories.existsBySlug(slug)) {
            throw new ConflictException("CATEGORY_TAKEN", "Ya existe una categoría con ese nombre");
        }
        Category c = categories.save(new Category(name, slug));
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), 0L);
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        String sku = r.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsBySkuIgnoreCase(sku)) {
            throw new ConflictException("SKU_TAKEN", "Ya existe un producto con el SKU " + sku);
        }
        Product p = new Product(sku, r.name().trim(), blankToNull(r.description()), r.price(),
                r.initialStock() == null ? 0 : r.initialStock(), blankToNull(r.imageUrl()), findCategory(r.categoryId()));
        if (r.active() != null) p.setActive(r.active());
        products.saveAndFlush(p);
        return mapper.toResponse(p);
    }

    /**
     * Edita datos del catálogo. No cambia el stock (eso es {@link #adjustStock}) ni afecta a pedidos ya
     * creados, que conservan su snapshot de nombre y precio.
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest r) {
        Product p = products.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        if (r.version() == null) {
            throw new IllegalArgumentException("version es obligatoria al editar un producto");
        }
        if (r.version() != p.getVersion()) {
            throw new ConflictException("CONCURRENT_MODIFICATION",
                    "Otro usuario modificó este producto. Recarga para ver los cambios");
        }
        String sku = r.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
            throw new ConflictException("SKU_TAKEN", "Ya existe un producto con el SKU " + sku);
        }
        p.setSku(sku);
        p.setName(r.name().trim());
        p.setDescription(blankToNull(r.description()));
        p.setPrice(r.price());
        p.setImageUrl(blankToNull(r.imageUrl()));
        p.setCategory(findCategory(r.categoryId()));
        if (r.active() != null) p.setActive(r.active());
        products.saveAndFlush(p);
        return mapper.toResponse(p);
    }

    @Transactional
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest r) {
        Product p = products.findWithCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        if (products.adjustStock(id, r.delta()) == 0) {
            int current = products.findStockById(id).orElse(0);
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "El ajuste dejaría el stock en negativo (stock actual: " + current + ")");
        }
        entityManager.refresh(p);
        log.info("Ajuste de stock producto={} delta={} motivo=\"{}\"", p.getSku(), r.delta(), r.reason());
        return mapper.toResponse(p);
    }

    private Category findCategory(Long id) {
        return categories.findById(id)
                .orElseThrow(() -> new BusinessRuleException("CATEGORY_NOT_FOUND", "La categoría no existe"));
    }

    static String slugify(String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
