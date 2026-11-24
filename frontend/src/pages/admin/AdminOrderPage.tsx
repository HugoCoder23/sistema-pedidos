import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { api, errorMessage } from '../../api/client';
import type { Order, OrderStatus } from '../../api/types';
import { useAsync } from '../../lib/useAsync';
import { STATUS_ACTION, STATUS_LABEL, fmtDateTime } from '../../lib/format';
import { Alert, Card, Empty, Field, Loading, Modal, PageHeader, StatusBadge } from '../../components/ui';
import { AddressBlock, HistoryTimeline, OrderItemsCard, OrderProgress, PaymentsList } from '../../components/OrderParts';

export function AdminOrderPage() {
  const { id } = useParams();
  const { data: order, error, loading, setData, reload } = useAsync(() => api.get<Order>(`/api/admin/orders/${id}`), [id]);
  const [target, setTarget] = useState<OrderStatus | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  if (loading && !order) return <Loading />;
  if (error || !order) return <Empty title="Pedido no encontrado"><p>{error}</p></Empty>;

  async function apply() {
    if (!target) return;
    setBusy(true);
    setActionError(null);
    try {
      const updated = await api.patch<Order>(`/api/admin/orders/${order!.id}`, { status: target, note: note || undefined });
      setData(updated);
      setDone(`Pedido actualizado a "${STATUS_LABEL[target]}". Se notificó al cliente.`);
      setTarget(null);
      setNote('');
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const open = (s: OrderStatus) => { setTarget(s); setActionError(null); setDone(null); };

  return (
    <>
      <Link to="/admin/pedidos" className="link back-link"><ArrowLeft size={16} /> Pedidos</Link>
      <PageHeader
        title={<span className="title-with-badge"><span className="mono">{order.number}</span> <StatusBadge status={order.status} /></span>}
        subtitle={`${order.customer.name} · ${order.customer.email} · ${fmtDateTime(order.createdAt)}`}
        actions={order.allowedTransitions.map((s) => (
          <button key={s} className={`btn ${s === 'CANCELLED' ? 'btn-ghost danger' : 'btn-primary'}`} onClick={() => open(s)}>
            {STATUS_ACTION[s]}
          </button>
        ))}
      />
      {done && <Alert tone="success">{done}</Alert>}
      {order.status === 'PENDING' && <Alert tone="info">Esperando el pago del cliente. Solo un pago aprobado lo marca como pagado.</Alert>}
      <Card><OrderProgress status={order.status} /></Card>
      <div className="two-col wide-left">
        <OrderItemsCard order={order} />
        <div>
          <Card title="Entrega"><AddressBlock address={order.shippingAddress} /></Card>
          <Card title="Pagos"><PaymentsList order={order} /></Card>
          <Card title="Historial de estados"><HistoryTimeline order={order} /></Card>
        </div>
      </div>
      {target && (
        <Modal title={STATUS_ACTION[target]} onClose={() => setTarget(null)}>
          <div className="form">
            <p>
              <StatusBadge status={order.status} /> → <StatusBadge status={target} />
            </p>
            {target === 'CANCELLED' && (
              <Alert tone="warning">
                Se devolverán {order.itemCount} unidades al inventario
                {order.payments.some((p) => p.status === 'APPROVED') ? ' y se reembolsará el pago (simulado)' : ''}.
              </Alert>
            )}
            {actionError && <Alert>{actionError} {actionError.includes('Recarga') && <button className="link" onClick={() => { reload(); setTarget(null); }}>Recargar</button>}</Alert>}
            <Field label={target === 'SHIPPED' ? 'Guía de rastreo / nota' : 'Nota (opcional)'}>
              <input value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} />
            </Field>
            <div className="form-actions">
              <button className="btn btn-ghost" onClick={() => setTarget(null)}>Cancelar</button>
              <button className={`btn ${target === 'CANCELLED' ? 'btn-danger' : 'btn-primary'}`} disabled={busy} onClick={apply}>
                {busy ? 'Guardando…' : 'Confirmar'}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
