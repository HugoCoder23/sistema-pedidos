import { Link, useParams } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { api } from '../api/client';
import type { Order } from '../api/types';
import { useAsync } from '../lib/useAsync';
import { fmtMoney } from '../lib/format';
import { Empty, Loading, StatusBadge } from '../components/ui';
import { AddressBlock, OrderProgress } from '../components/OrderParts';

export function SuccessPage() {
  const { id } = useParams();
  const { data: order, error, loading } = useAsync(() => api.get<Order>(`/api/orders/${id}`), [id]);

  if (loading) return <Loading />;
  if (error || !order) return <Empty title="Pedido no encontrado"><p>{error}</p></Empty>;

  return (
    <div className="success">
      <CheckCircle2 size={56} className="success-icon" />
      <h1>¡Gracias por tu compra!</h1>
      <p className="muted">Te enviamos la confirmación a {order.customer.email} (correo simulado).</p>
      <div className="success-number">
        <span className="muted small">Número de pedido</span>
        <span className="mono big">{order.number}</span>
        <StatusBadge status={order.status} />
      </div>
      <OrderProgress status={order.status} />
      <div className="success-grid">
        <div><span className="muted small">Total pagado</span><div className="strong">{fmtMoney(order.total)}</div></div>
        <div><span className="muted small">Artículos</span><div className="strong">{order.itemCount}</div></div>
        <div><span className="muted small">Entrega en</span><AddressBlock address={order.shippingAddress} /></div>
      </div>
      <div className="page-actions center">
        <Link to={`/cuenta/pedidos/${order.id}`} className="btn btn-primary">Ver detalle del pedido</Link>
        <Link to="/" className="btn btn-ghost">Seguir comprando</Link>
      </div>
    </div>
  );
}
