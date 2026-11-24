package com.portafolio.pedidos.repository;

import com.portafolio.pedidos.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    /**
     * Descuenta stock de forma atómica. La condición {@code stock >= :qty} se evalúa dentro del
     * mismo UPDATE (con bloqueo de fila), por lo que dos compras simultáneas de las últimas
     * unidades no pueden ambas tener éxito: la segunda afecta 0 filas.
     *
     * @return filas afectadas (1 = descontado, 0 = sin stock suficiente o producto inactivo)
     */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty "
            + "where p.id = :id and p.active = true and p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    /** Devuelve unidades al inventario (cancelación de un pedido con stock reservado). */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);

    /** Ajuste manual del administrador; nunca deja el stock negativo. */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :delta where p.id = :id and p.stock + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta);

    @Query("select p.stock from Product p where p.id = :id")
    Optional<Integer> findStockById(@Param("id") Long id);

    @EntityGraph(attributePaths = "category")
    List<Product> findTop8ByActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(int threshold);

    long countByActiveTrue();
}
