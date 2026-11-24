import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { CreditCard, ShieldCheck, XCircle } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Order, PaymentResult } from '../api/types';
import { useAsync } from '../lib/useAsync';
import { fmtMoney } from '../lib/format';
import { Alert, Card, Empty, Loading, PageHeader } from '../components/ui';
import { PaymentsList, Totals } from '../components/OrderParts';

/**
 * Pasarela SIMULADA: no hay campos de tarjeta. Quien prueba la demo elige el resultado del pago
 * y el backend toma el importe del pedido.
 */
export function PaymentPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: order, error, loading, setData } = useAsync(() => api.get<Order>(`/api/orders/${id}`), [id]);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  if (loading) return <Loading />;
  if (error || !order) return <Empty title="Pedido no encontrado"><p>{error}</p></Empty>;
  if (!order.canPay) return <Navigate to={`/cuenta/pedidos/${order.id}`} replace />;

  async function pay(outcome: 'APPROVED' | 'DECLINED') {
    setBusy(true);
    setMsg(null);
    try {
      const res = await api.post<PaymentResult>('/api/payments/simulate', { orderId: order!.id, outcome });
      if (res.payment.status === 'APPROVED') {
        navigate(`/pedidos/${order!.id}/confirmacion`, { replace: true });
        return;
      }
      setData(res.order);
      setMsg(res.payment.failureReason ?? 'El pago fue rechazado');
    } catch (e) {
      setMsg(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Pago" subtitle={<>Pedido <span className="mono">{order.number}</span> · reservado para ti</>} />
      <div className="two-col">
        <Card title="Método de pago">
          <div className="sim-card">
            <CreditCard size={28} />
            <div>
              <div className="strong">Pasarela de pago simulada</div>
              <div className="muted small">Entorno de prueba: no se solicitan ni guardan datos de tarjeta.</div>
            </div>
          </div>
          {msg && <Alert>{msg}. Tu pedido sigue pendiente; puedes intentarlo de nuevo.</Alert>}
          <div className="pay-actions">
            <button className="btn btn-primary btn-lg" disabled={busy} onClick={() => pay('APPROVED')}>
              <ShieldCheck size={18} /> {busy ? 'Procesando…' : `Pagar ${fmtMoney(order.total)}`}
            </button>
            <button className="btn btn-ghost" disabled={busy} onClick={() => pay('DECLINED')}>
              <XCircle size={16} /> Simular pago rechazado
            </button>
          </div>
          {order.payments.length > 0 && <><h3 className="subhead">Intentos</h3><PaymentsList order={order} /></>}
          <p className="muted small">
            ¿Prefieres pagar después? Tu pedido queda <strong>pendiente</strong> en <Link to={`/cuenta/pedidos/${order.id}`} className="link">Mis pedidos</Link>.
          </p>
        </Card>
        <Card title="Resumen" className="summary-card">
          <ul className="mini-lines">
            {order.items.map((i) => <li key={i.productId}><span>{i.quantity} × {i.name}</span><span>{fmtMoney(i.lineTotal)}</span></li>)}
          </ul>
          <Totals subtotal={order.subtotal} shipping={order.shippingCost} total={order.total} />
        </Card>
      </div>
    </>
  );
}
