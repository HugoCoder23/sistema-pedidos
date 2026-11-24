import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Search } from 'lucide-react';
import { api } from '../../api/client';
import type { OrderStatus, OrderSummary, Page } from '../../api/types';
import { useAsync, useDebounced } from '../../lib/useAsync';
import { STATUS_LABEL, fmtDateTime, fmtMoney } from '../../lib/format';
import { Alert, Card, Empty, Loading, PageHeader, Pagination, StatusBadge } from '../../components/ui';

const SIZE = 15;

export function AdminOrdersPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const status = (params.get('estado') ?? '') as OrderStatus | '';
  const from = params.get('desde') ?? '';
  const to = params.get('hasta') ?? '';
  const page = Number(params.get('pagina') ?? 0);
  const [text, setText] = useState(params.get('q') ?? '');
  const q = useDebounced(text.trim(), 300);

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('pagina' in patch)) next.delete('pagina');
    setParams(next, { replace: true });
  };

  const { data, error, loading } = useAsync(() => api.get<Page<OrderSummary>>('/api/admin/orders', {
    status, q, from, to, page, size: SIZE,
  }), [status, q, from, to, page]);

  return (
    <>
      <PageHeader title="Pedidos" subtitle="Filtra, revisa y actualiza el estado de los pedidos" />
      <Card>
        <div className="filters">
          <div className="search">
            <Search size={16} />
            <input value={text} onChange={(e) => { setText(e.target.value); update({ q: e.target.value.trim() || null }); }}
              placeholder="Número de pedido, cliente o email" aria-label="Buscar pedidos" />
          </div>
          <select value={status} onChange={(e) => update({ estado: e.target.value || null })} aria-label="Estado">
            <option value="">Todos los estados</option>
            {(Object.keys(STATUS_LABEL) as OrderStatus[]).map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
          <label className="date-field">Desde <input type="date" value={from} max={to || undefined} onChange={(e) => update({ desde: e.target.value || null })} /></label>
          <label className="date-field">Hasta <input type="date" value={to} min={from || undefined} onChange={(e) => update({ hasta: e.target.value || null })} /></label>
        </div>
        <div className="status-tabs">
          {(['PAID', 'PROCESSING', 'SHIPPED'] as OrderStatus[]).map((s) => (
            <button key={s} className={`chip ${status === s ? 'active' : ''}`} onClick={() => update({ estado: status === s ? null : s })}>
              {s === 'PAID' ? 'Por preparar' : s === 'PROCESSING' ? 'Por enviar' : 'En camino'}
            </button>
          ))}
        </div>
        {error && <Alert>{error}</Alert>}
        {loading && !data ? <Loading /> : data && (data.content.length === 0 ? <Empty title="Sin pedidos con esos filtros" /> : (
          <div className={`table-wrap ${loading ? 'is-loading' : ''}`}>
            <table className="table table-hover">
              <thead><tr><th>Pedido</th><th>Cliente</th><th>Fecha</th><th>Estado</th><th className="num">Art.</th><th className="num">Total</th></tr></thead>
              <tbody>
                {data.content.map((o) => (
                  <tr key={o.id} onClick={() => navigate(`/admin/pedidos/${o.id}`)}>
                    <td><Link to={`/admin/pedidos/${o.id}`} className="mono link-plain" onClick={(e) => e.stopPropagation()}>{o.number}</Link></td>
                    <td><div>{o.customer.name}</div><div className="muted small">{o.customer.email}</div></td>
                    <td className="nowrap">{fmtDateTime(o.createdAt)}</td>
                    <td><StatusBadge status={o.status} /></td>
                    <td className="num">{o.itemCount}</td>
                    <td className="num strong">{fmtMoney(o.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={SIZE}
          onPage={(p) => update({ pagina: p ? String(p) : null })} />}
      </Card>
    </>
  );
}
