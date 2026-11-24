import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Mail, PackageOpen } from 'lucide-react';
import { api } from '../api/client';
import type { Notification, OrderStatus, OrderSummary, Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../lib/useAsync';
import { STATUS_LABEL, fmtDate, fmtDateTime, fmtMoney } from '../lib/format';
import { Alert, Card, Empty, Loading, PageHeader, Pagination, StatusBadge } from '../components/ui';

const SIZE = 10;

export function AccountPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const orders = useAsync(() => api.get<Page<OrderSummary>>('/api/orders/my', { status, page, size: SIZE }), [status, page]);
  const mails = useAsync(() => api.get<Notification[]>('/api/notifications/my'), []);

  return (
    <>
      <PageHeader title="Mis pedidos" subtitle={`Hola, ${user?.name}. Aquí ves el estado de tus compras.`} />
      <div className="two-col wide-left">
        <Card title="Historial" actions={
          <select value={status} onChange={(e) => { setStatus(e.target.value as OrderStatus | ''); setPage(0); }} aria-label="Filtrar por estado">
            <option value="">Todos los estados</option>
            {(Object.keys(STATUS_LABEL) as OrderStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        }>
          {orders.error && <Alert>{orders.error}</Alert>}
          {orders.loading && !orders.data ? <Loading /> : orders.data && (
            orders.data.content.length === 0 ? (
              <Empty icon={<PackageOpen size={32} />} title="Aún no hay pedidos">
                <Link to="/" className="btn btn-primary">Ir al catálogo</Link>
              </Empty>
            ) : (
              <div className="table-wrap">
                <table className="table table-hover">
                  <thead><tr><th>Pedido</th><th>Fecha</th><th>Estado</th><th className="num">Artículos</th><th className="num">Total</th></tr></thead>
                  <tbody>
                    {orders.data.content.map((o) => (
                      <tr key={o.id} onClick={() => navigate(`/cuenta/pedidos/${o.id}`)}>
                        <td><Link to={`/cuenta/pedidos/${o.id}`} className="mono link-plain">{o.number}</Link></td>
                        <td className="nowrap">{fmtDate(o.createdAt)}</td>
                        <td><StatusBadge status={o.status} /></td>
                        <td className="num">{o.itemCount}</td>
                        <td className="num strong">{fmtMoney(o.total)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}
          {orders.data && <Pagination page={orders.data.page} totalPages={orders.data.totalPages}
            totalElements={orders.data.totalElements} size={SIZE} onPage={setPage} />}
        </Card>
        <Card title={<><Mail size={16} /> Correos recibidos</>}>
          <p className="muted small">Notificaciones simuladas: en la demo no se envían emails reales.</p>
          {mails.loading ? <Loading /> : mails.data?.length === 0 ? <p className="muted">Sin correos todavía.</p> : (
            <ul className="mail-list">
              {mails.data?.map((m) => (
                <li key={m.id}>
                  <div className="strong small">{m.subject}</div>
                  <div className="small">{m.body}</div>
                  <div className="muted small">{fmtDateTime(m.createdAt)}</div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  );
}
