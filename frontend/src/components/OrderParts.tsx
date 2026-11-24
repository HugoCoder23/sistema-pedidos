import { Check, X } from 'lucide-react';
import type { Address, Order, OrderStatus } from '../api/types';
import { ORDER_FLOW, PAYMENT_LABEL, STATUS_LABEL, fmtDateTime, fmtMoney } from '../lib/format';
import { Card, ProductImage } from './ui';

/** Barra de progreso del pedido (PENDING → DELIVERED); si se canceló lo indica. */
export function OrderProgress({ status }: { status: OrderStatus }) {
  if (status === 'CANCELLED') {
    return <div className="progress cancelled"><X size={16} /> Este pedido fue cancelado</div>;
  }
  const current = ORDER_FLOW.indexOf(status);
  return (
    <ol className="progress" aria-label="Progreso del pedido">
      {ORDER_FLOW.map((s, i) => (
        <li key={s} className={i < current ? 'done' : i === current ? 'current' : ''}>
          <span className="progress-dot">{i < current ? <Check size={12} /> : i + 1}</span>
          <span className="progress-label">{STATUS_LABEL[s]}</span>
        </li>
      ))}
    </ol>
  );
}

export function OrderItemsCard({ order, categoryOf }: { order: Order; categoryOf?: (sku: string) => string }) {
  return (
    <Card title={`Artículos (${order.itemCount})`}>
      <ul className="line-list">
        {order.items.map((i) => (
          <li key={i.productId} className="line">
            <ProductImage alt={i.name} category={categoryOf?.(i.sku) ?? skuCategory(i.sku)} size="sm" />
            <div className="line-main">
              <div className="strong">{i.name}</div>
              <div className="muted small">SKU {i.sku} · {i.quantity} × {fmtMoney(i.unitPrice)}</div>
            </div>
            <div className="line-total">{fmtMoney(i.lineTotal)}</div>
          </li>
        ))}
      </ul>
      <Totals subtotal={order.subtotal} shipping={order.shippingCost} total={order.total} />
      <p className="muted small">Precios congelados al confirmar el pedido; cambios posteriores del catálogo no los afectan.</p>
    </Card>
  );
}

export function Totals({ subtotal, shipping, total, label = 'Total' }:
  { subtotal: number; shipping: number; total: number; label?: string }) {
  return (
    <dl className="totals">
      <dt>Subtotal</dt><dd>{fmtMoney(subtotal)}</dd>
      <dt>Envío</dt><dd>{shipping === 0 ? <span className="text-green">Gratis</span> : fmtMoney(shipping)}</dd>
      <dt className="total">{label}</dt><dd className="total">{fmtMoney(total)}</dd>
    </dl>
  );
}

export function AddressBlock({ address }: { address: Address }) {
  return (
    <address className="address">
      <strong>{address.recipient}</strong><br />
      {address.line1}{address.line2 && <>, {address.line2}</>}<br />
      {address.city}, {address.state} · CP {address.postalCode}<br />
      Tel. {address.phone}
      {address.instructions && <><br /><span className="muted">“{address.instructions}”</span></>}
    </address>
  );
}

export function HistoryTimeline({ order }: { order: Order }) {
  return (
    <ol className="timeline">
      {[...order.history].reverse().map((h, idx) => (
        <li key={idx} className={`timeline-item tone-${h.to.toLowerCase()}`}>
          <div className="timeline-dot" />
          <div>
            <div className="strong">{STATUS_LABEL[h.to]}</div>
            <div className="muted small">{fmtDateTime(h.changedAt)} · {h.actor}</div>
            {h.note && <div className="small">{h.note}</div>}
          </div>
        </li>
      ))}
    </ol>
  );
}

export function PaymentsList({ order }: { order: Order }) {
  if (order.payments.length === 0) return <p className="muted small">Sin intentos de pago.</p>;
  return (
    <ul className="payments">
      {order.payments.map((p) => (
        <li key={p.id}>
          <span className={`badge badge-${p.status === 'APPROVED' ? 'green' : p.status === 'REFUNDED' ? 'blue' : 'red'}`}>
            {PAYMENT_LABEL[p.status]}
          </span>
          <span className="mono small">{p.reference}</span>
          <span className="strong">{fmtMoney(p.amount)}</span>
          <span className="muted small">{fmtDateTime(p.createdAt)}{p.failureReason ? ` · ${p.failureReason}` : ''}</span>
        </li>
      ))}
    </ul>
  );
}

/** Los SKUs demo usan un prefijo por categoría (AUD-, CAL-…): sirve para elegir la ilustración. */
export function skuCategory(sku: string) {
  const prefix = sku.slice(0, 3).toUpperCase();
  return ({ AUD: 'audio', CAL: 'calzado', REL: 'relojes', COM: 'computacion', HOG: 'hogar' } as Record<string, string>)[prefix] ?? '';
}
