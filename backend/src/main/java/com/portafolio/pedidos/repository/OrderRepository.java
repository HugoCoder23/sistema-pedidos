package com.portafolio.pedidos.repository;

import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = "customer")
    Optional<Order> findWithCustomerById(Long id);

    /** Solo encuentra el pedido si pertenece al cliente indicado (evita IDOR). */
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    boolean existsByNumber(String number);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupedByStatus();

    List<Order> findByStatusInAndCreatedAtGreaterThanEqual(Collection<OrderStatus> statuses, Instant from);

    @Query("select i.productId, max(i.productNameSnapshot), sum(i.quantity), sum(i.lineTotal) "
            + "from OrderItem i where i.order.status in :statuses and i.order.createdAt >= :from "
            + "group by i.productId order by sum(i.quantity) desc, sum(i.lineTotal) desc")
    List<Object[]> topProducts(@Param("statuses") Collection<OrderStatus> statuses, @Param("from") Instant from,
                               Pageable limit);
}
