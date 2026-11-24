import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ShoppingCart, Truck } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Product } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useAsync } from '../lib/useAsync';
import { fmtMoney } from '../lib/format';
import { Alert, AvailabilityBadge, Empty, Loading, ProductImage, QtyStepper } from '../components/ui';

const MAX_PER_ITEM = 20;

export function ProductPage() {
  const { id } = useParams();
  const { user, can } = useAuth();
  const { add, cart } = useCart();
  const navigate = useNavigate();
  const { data: p, error, loading } = useAsync(() => api.get<Product>(`/api/products/${id}`), [id]);
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  if (loading) return <Loading />;
  if (error || !p) return <Empty title="Producto no disponible"><p>{error}</p><Link to="/" className="link">Volver al catálogo</Link></Empty>;

  const inCart = cart?.items.find((i) => i.productId === p.id)?.quantity ?? 0;
  const max = Math.max(1, Math.min(MAX_PER_ITEM, p.stock) - inCart);
  const soldOut = p.stock === 0;

  async function addToCart() {
    if (!user) { navigate('/login', { state: { from: `/productos/${id}` } }); return; }
    setBusy(true);
    setMsg(null);
    try {
      await add(p!.id, qty);
      setMsg({ tone: 'success', text: `Agregaste ${qty} × ${p!.name} al carrito.` });
      setQty(1);
    } catch (e) {
      setMsg({ tone: 'error', text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Link to="/" className="link back-link"><ArrowLeft size={16} /> Catálogo</Link>
      <div className="product-detail">
        <ProductImage src={p.imageUrl} alt={p.name} category={p.category.slug} size="lg" />
        <div className="product-info">
          <Link to={`/?categoria=${p.category.slug}`} className="product-category">{p.category.name}</Link>
          <h1>{p.name}</h1>
          <span className="muted small mono">SKU {p.sku}</span>
          <div className="price price-lg">{fmtMoney(p.price)}</div>
          <AvailabilityBadge availability={p.availability} stock={p.stock} />
          {p.description && <p className="description">{p.description}</p>}

          {msg && <Alert tone={msg.tone}>{msg.text} {msg.tone === 'success' && <Link to="/carrito" className="link">Ver carrito</Link>}</Alert>}

          {(!user || can('CUSTOMER')) && (
            <div className="buy-box">
              {!soldOut && <QtyStepper value={qty} max={max} onChange={setQty} disabled={busy} />}
              <button className="btn btn-primary btn-lg" disabled={soldOut || busy || inCart >= Math.min(MAX_PER_ITEM, p.stock)} onClick={addToCart}>
                <ShoppingCart size={18} /> {soldOut ? 'Agotado' : busy ? 'Agregando…' : 'Agregar al carrito'}
              </button>
            </div>
          )}
          {inCart > 0 && <p className="muted small">Ya tienes {inCart} en tu carrito.</p>}
          <p className="perk"><Truck size={16} /> Envío gratis en compras desde {fmtMoney(1500)}</p>
        </div>
      </div>
    </>
  );
}
