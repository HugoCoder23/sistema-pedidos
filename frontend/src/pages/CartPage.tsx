import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ShoppingCart, Trash2, Truck } from 'lucide-react';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { fmtMoney } from '../lib/format';
import { Alert, Card, Empty, Loading, PageHeader, ProductImage, QtyStepper } from '../components/ui';
import { Totals } from '../components/OrderParts';

const MAX_PER_ITEM = 20;

export function CartPage() {
  const { user, ready } = useAuth();
  const { cart, update, remove } = useCart();
  const navigate = useNavigate();
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!ready) return <Loading />;
  if (!user) {
    return (
      <Empty icon={<ShoppingCart size={36} />} title="Inicia sesión para usar tu carrito">
        <p>Tu carrito se guarda en tu cuenta para que no pierdas nada entre dispositivos.</p>
        <Link to="/login" state={{ from: '/carrito' }} className="btn btn-primary">Iniciar sesión</Link>
      </Empty>
    );
  }
  if (!cart) return <Loading />;
  if (cart.items.length === 0) {
    return (
      <Empty icon={<ShoppingCart size={36} />} title="Tu carrito está vacío">
        <p>Agrega productos desde el catálogo.</p>
        <Link to="/" className="btn btn-primary">Explorar catálogo</Link>
      </Empty>
    );
  }

  async function run(id: number, fn: () => Promise<unknown>) {
    setBusyId(id);
    setError(null);
    try { await fn(); } catch (e) { setError(errorMessage(e)); } finally { setBusyId(null); }
  }

  const progress = Math.min(100, (cart.subtotal / cart.freeShippingFrom) * 100);

  return (
    <>
      <PageHeader title="Carrito" subtitle={`${cart.itemCount} artículo${cart.itemCount === 1 ? '' : 's'}`} />
      {error && <Alert>{error}</Alert>}
      <div className="two-col">
        <Card>
          <ul className="line-list">
            {cart.items.map((i) => (
              <li key={i.id} className={`line ${i.issue ? 'has-issue' : ''}`}>
                <ProductImage src={i.imageUrl} alt={i.name} category={i.categorySlug} size="sm" />
                <div className="line-main">
                  <Link to={`/productos/${i.productId}`} className="strong link-plain">{i.name}</Link>
                  <div className="muted small">{fmtMoney(i.unitPrice)} c/u</div>
                  {i.issue && <div className="line-issue">{i.issue}</div>}
                </div>
                <QtyStepper value={i.quantity} max={Math.min(MAX_PER_ITEM, Math.max(i.stock, i.quantity))}
                  disabled={busyId === i.id || !i.active}
                  onChange={(q) => run(i.id, () => update(i.id, q))} />
                <div className="line-total">{fmtMoney(i.lineTotal)}</div>
                <button className="btn btn-ghost btn-icon danger" aria-label={`Quitar ${i.name}`} disabled={busyId === i.id}
                  onClick={() => run(i.id, () => remove(i.id))}><Trash2 size={16} /></button>
              </li>
            ))}
          </ul>
        </Card>
        <Card title="Resumen" className="summary-card">
          <Totals subtotal={cart.subtotal} shipping={cart.shippingCost} total={cart.total} label="Total estimado" />
          <div className="free-ship">
            <Truck size={16} />
            {cart.amountForFreeShipping > 0
              ? <span>Te faltan <strong>{fmtMoney(cart.amountForFreeShipping)}</strong> para envío gratis</span>
              : <span className="text-green strong">¡Tienes envío gratis!</span>}
          </div>
          <div className="meter"><span style={{ width: `${progress}%` }} /></div>
          {!cart.canCheckout && <Alert tone="warning">Corrige los artículos marcados para continuar.</Alert>}
          <button className="btn btn-primary btn-block" disabled={!cart.canCheckout} onClick={() => navigate('/checkout')}>
            Continuar al pago
          </button>
          <p className="muted small center">El total final se confirma al crear el pedido.</p>
        </Card>
      </div>
    </>
  );
}
