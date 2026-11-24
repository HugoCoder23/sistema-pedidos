import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DollarSign, ShoppingBag, Receipt, Package, AlertTriangle } from 'lucide-react';
import { api } from '../../api/client';
import type { OrderStatus, Report } from '../../api/types';
import { useAsync } from '../../lib/useAsync';
import { STATUS_LABEL, STATUS_TONE, fmtDay, fmtMoney, fmtNumber } from '../../lib/format';
import { Alert, Card, Loading, PageHeader } from '../../components/ui';

const RANGES = [7, 14, 30];

export function AdminDashboardPage() {
  const [days, setDays] = useState(14);
  const { data: r, error, loading } = useAsync(() => api.get<Report>('/api/admin/reports/summary', { days }), [days]);

  return (
    <>
      <PageHeader title="Panel de la tienda" subtitle="Ventas confirmadas (pagadas y no canceladas)"
        actions={<div className="segmented">
          {RANGES.map((d) => <button key={d} className={`seg ${d === days ? 'active' : ''}`} onClick={() => setDays(d)}>{d} días</button>)}
        </div>} />
      {error && <Alert>{error}</Alert>}
      {loading && !r ? <Loading /> : r && (
        <>
          <div className="stats">
            <Stat icon={<DollarSign size={20} />} tone="green" label="Ventas" value={fmtMoney(r.revenue)} />
            <Stat icon={<ShoppingBag size={20} />} tone="blue" label="Pedidos pagados" value={fmtNumber(r.paidOrders)} />
            <Stat icon={<Receipt size={20} />} tone="violet" label="Ticket promedio" value={fmtMoney(r.averageTicket)} />
            <Stat icon={<Package size={20} />} tone="amber" label="Productos activos" value={fmtNumber(r.activeProducts)} />
          </div>
          <div className="two-col wide-left">
            <Card title="Ventas por día"><SalesChart data={r.salesByDay} /></Card>
            <Card title="Pedidos por estado">
              <ul className="status-list">
                {(Object.keys(STATUS_LABEL) as OrderStatus[]).map((s) => (
                  <li key={s}>
                    <Link to={`/admin/pedidos?estado=${s}`} className="link-plain">
                      <span className={`dot dot-${STATUS_TONE[s]}`} /> {STATUS_LABEL[s]}
                    </Link>
                    <span className="strong">{r.ordersByStatus[s] ?? 0}</span>
                  </li>
                ))}
              </ul>
            </Card>
          </div>
          <div className="two-col">
            <Card title="Más vendidos">
              {r.topProducts.length === 0 ? <p className="muted">Sin ventas en el periodo.</p> : (
                <table className="table">
                  <thead><tr><th>Producto</th><th className="num">Unidades</th><th className="num">Importe</th></tr></thead>
                  <tbody>{r.topProducts.map((t) => (
                    <tr key={t.productId}><td>{t.name}</td><td className="num">{t.units}</td><td className="num">{fmtMoney(t.revenue)}</td></tr>
                  ))}</tbody>
                </table>
              )}
            </Card>
            <Card title={<><AlertTriangle size={16} /> Stock bajo</>} actions={<Link to="/admin/productos" className="link">Inventario</Link>}>
              {r.lowStock.length === 0 ? <p className="muted">Todo el inventario está sano.</p> : (
                <ul className="status-list">
                  {r.lowStock.map((p) => (
                    <li key={p.id}><span>{p.name} <span className="muted small mono">{p.sku}</span></span>
                      <span className={`badge ${p.stock === 0 ? 'badge-red' : 'badge-amber'}`}>{p.stock === 0 ? 'Agotado' : `${p.stock} u.`}</span></li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  );
}

function Stat({ icon, label, value, tone }: { icon: React.ReactNode; label: string; value: string; tone: string }) {
  return (
    <div className="stat">
      <div className={`stat-icon tone-${tone}`}>{icon}</div>
      <div><div className="stat-value">{value}</div><div className="stat-label">{label}</div></div>
    </div>
  );
}

function SalesChart({ data }: { data: Report['salesByDay'] }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 640, H = 220, P = { l: 8, r: 8, t: 12, b: 26 };
  const max = Math.max(1, ...data.map((d) => d.revenue));
  const bw = (W - P.l - P.r) / data.length;
  const y = (v: number) => H - P.b - (v / max) * (H - P.t - P.b);
  const step = Math.ceil(data.length / 7);
  const h = hover === null ? null : data[hover];
  return (
    <div className="chart">
      <div className="chart-tip">{h ? `${fmtDay(h.day)}: ${fmtMoney(h.revenue)} · ${h.orders} pedido${h.orders === 1 ? '' : 's'}` : 'Pasa el cursor sobre una barra'}</div>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Ventas por día" onMouseLeave={() => setHover(null)}>
        {[0.25, 0.5, 0.75, 1].map((f) => <line key={f} className="grid-line" x1={P.l} x2={W - P.r} y1={y(max * f)} y2={y(max * f)} />)}
        {data.map((d, i) => {
          const x = P.l + i * bw;
          return (
            <g key={d.day} onMouseEnter={() => setHover(i)}>
              {hover === i && <rect className="hover-band" x={x} y={P.t} width={bw} height={H - P.t - P.b} />}
              <rect className="bar" x={x + bw * 0.18} y={y(d.revenue)} width={bw * 0.64} height={Math.max(0, H - P.b - y(d.revenue))} rx={3} />
              {i % step === 0 && <text className="axis-label" x={x + bw / 2} y={H - 8} textAnchor="middle">{fmtDay(d.day)}</text>}
              <rect className="hit" x={x} y={0} width={bw} height={H} />
            </g>
          );
        })}
      </svg>
    </div>
  );
}
