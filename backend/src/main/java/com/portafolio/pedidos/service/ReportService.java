package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.Responses.DailySales;
import com.portafolio.pedidos.dto.Responses.ReportResponse;
import com.portafolio.pedidos.dto.Responses.TopProduct;
import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.OrderRepository;
import com.portafolio.pedidos.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Reportes básicos del panel administrativo. */
@Service
public class ReportService {

    /** Zona de la tienda para agrupar ventas por día. */
    public static final ZoneId STORE_ZONE = ZoneId.of("America/Mexico_City");

    /** Estados que cuentan como venta (pago aprobado y no cancelado). */
    static final Set<OrderStatus> SOLD =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orders;
    private final ProductRepository products;
    private final OrderMapper mapper;

    public ReportService(OrderRepository orders, ProductRepository products, OrderMapper mapper) {
        this.orders = orders;
        this.products = products;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ReportResponse summary(int days) {
        if (days < 1 || days > 365) throw new IllegalArgumentException("days debe estar entre 1 y 365");
        LocalDate today = LocalDate.now(STORE_ZONE);
        LocalDate firstDay = today.minusDays(days - 1L);
        Instant from = firstDay.atStartOfDay(STORE_ZONE).toInstant();

        List<Order> sold = orders.findByStatusInAndCreatedAtGreaterThanEqual(SOLD, from);
        BigDecimal revenue = sold.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal avg = sold.isEmpty() ? BigDecimal.ZERO.setScale(2)
                : revenue.divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP);

        Map<LocalDate, long[]> countByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) {
            countByDay.put(d, new long[1]);
            revenueByDay.put(d, BigDecimal.ZERO.setScale(2));
        }
        for (Order o : sold) {
            LocalDate d = o.getCreatedAt().atZone(STORE_ZONE).toLocalDate();
            if (!countByDay.containsKey(d)) continue;
            countByDay.get(d)[0]++;
            revenueByDay.merge(d, o.getTotal(), BigDecimal::add);
        }
        List<DailySales> byDay = countByDay.entrySet().stream()
                .map(e -> new DailySales(e.getKey(), e.getValue()[0], revenueByDay.get(e.getKey())))
                .toList();

        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) byStatus.put(s, 0L);
        orders.countGroupedByStatus().forEach(r -> byStatus.put((OrderStatus) r[0], (Long) r[1]));

        List<TopProduct> top = orders.topProducts(SOLD, from, PageRequest.of(0, 5)).stream()
                .map(r -> new TopProduct((Long) r[0], (String) r[1], ((Number) r[2]).longValue(), (BigDecimal) r[3]))
                .toList();

        return new ReportResponse(days, revenue, sold.size(), avg, products.countByActiveTrue(), byStatus, byDay, top,
                products.findTop8ByActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(OrderMapper.LOW_STOCK_THRESHOLD)
                        .stream().map(mapper::toResponse).toList());
    }
}
