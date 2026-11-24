import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CreditCard } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Order } from '../api/types';
import { useAsync } from '../lib/useAsync';
import { fmtDateTime } from '../lib/format';
import { Alert, Card, Empty, Field, Loading, Modal, PageHeader, StatusBadge } from '../components/ui';
import { AddressBlock, HistoryTimeline, OrderItemsCard, OrderProgress, PaymentsList } from '../components/OrderParts';

export function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: order, error, loading, setData } = useAsync(() => api.get<Order>(`/api/orders/${id}`), [id]);
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  if (loading) return <Loading />;
  if (error || !order) {
    return <Empty title="Pedido no encontrado"><p>{error}</p><Link to="/cuenta" className="link">Volver a mis pedidos</Link></Empty>;
  }

  async function cancel() {
    setBusy(true);
    setActionError(null);
    try {
      const updated = await api.post<Order>(`/api/orders/${order!.id}/cancel`, { reason: reason || undefined });
      setData(updated);
      setConfirming(false);
      setDone('Pedido cancelado. Los artículos regresaron al inventario' + (order!.status === 'PAID' ? ' y el pago se reembolsó.' : '.'));
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Link to="/cuenta" className="link back-link"><ArrowLeft size={16} /> Mis pedidos</Link>
      <PageHeader
        title={<span className="title-with-badge"><span className="mono">{order.number}</span> <StatusBadge status={order.status} /></span>}
        subtitle={`Realizado el ${fmtDateTime(order.createdAt)}`}
        actions={<>
          {order.canPay && <button className="btn btn-primary" onClick={() => navigate(`/pedidos/${order.id}/pago`)}><CreditCard size={16} /> Pagar ahora</button>}
          {order.canCancel && <button className="btn btn-ghost danger" onClick={() => setConfirming(true)}>Cancelar pedido</button>}
        </>}
      />
      {done && <Alert tone="success">{done}</Alert>}
      <Card><OrderProgress status={order.status} /></Card>
      <div className="two-col wide-left">
        <OrderItemsCard order={order} />
        <div>
          <Card title="Entrega"><AddressBlock address={order.shippingAddress} /></Card>
          <Card title="Pagos"><PaymentsList order={order} /></Card>
          <Card title="Historial"><HistoryTimeline order={order} /></Card>
        </div>
      </div>
      {confirming && (
        <Modal title="¿Cancelar el pedido?" onClose={() => setConfirming(false)}>
          <div className="form">
            <p>Se liberarán los artículos reservados{order.status === 'PAID' ? ' y se reembolsará tu pago (simulado)' : ''}. Esta acción no se puede deshacer.</p>
            {actionError && <Alert>{actionError}</Alert>}
            <Field label="Motivo (opcional)"><input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} /></Field>
            <div className="form-actions">
              <button className="btn btn-ghost" onClick={() => setConfirming(false)}>Volver</button>
              <button className="btn btn-danger" disabled={busy} onClick={cancel}>{busy ? 'Cancelando…' : 'Sí, cancelar'}</button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
