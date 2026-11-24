import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Search, ShoppingBag, Truck, ShieldCheck, RotateCcw, PackageSearch } from 'lucide-react';
import { api, errorMessage } from '../api/client';
import type { Category, Page, Product } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { useAsync, useDebounced } from '../lib/useAsync';
import { fmtMoney } from '../lib/format';
import { Alert, AvailabilityBadge, Empty, Loading, Pagination, ProductImage, Toast } from '../components/ui';

const PAGE_SIZE = 12;

export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const q = useDebounced(text.trim(), 300);
  const category = params.get('categoria') ?? '';
  const sort = params.get('orden') ?? '';
  const inStock = params.get('disponibles') === '1';
  const page = Number(params.get('pagina') ?? 0);

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    if (!('pagina' in patch)) next.delete('pagina');
    setParams(next, { replace: true });
  };

  const categories = useAsync(() => api.get<Category[]>('/api/categories'), []);
  const products = useAsync(() => api.get<Page<Product>>('/api/products', {
    q, category, sort, inStock: inStock || undefined, page, size: PAGE_SIZE,
  }), [q, category, sort, inStock, page]);

  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  return (
    <>
      {!q && !category && page === 0 && (
        <section className="hero">
          <div className="hero-text">
            <span className="hero-kicker">Tienda de demostración</span>
            <h1>Tus productos favoritos, en un solo lugar</h1>
            <p>Explora el catálogo, arma tu carrito y sigue tu pedido desde el pago hasta la entrega.</p>
            <a href="#catalogo" className="btn btn-primary btn-lg"><ShoppingBag size={18} /> Comprar ahora</a>
          </div>
          <ul className="hero-perks">
            <li><Truck size={18} /> Envío gratis desde $1,500</li>
            <li><ShieldCheck size={18} /> Pago simulado y seguro</li>
            <li><RotateCcw size={18} /> Cancela antes de la preparación</li>
          </ul>
        </section>
      )}

      <section id="catalogo" className="catalog-toolbar">
        <div className="search">
          <Search size={16} />
          <input value={text} onChange={(e) => { setText(e.target.value); update({ q: e.target.value.trim() || null }); }}
            placeholder="Buscar por nombre, SKU o descripción" aria-label="Buscar productos" />
        </div>
        <select value={sort} onChange={(e) => update({ orden: e.target.value || null })} aria-label="Ordenar">
          <option value="">Nombre (A-Z)</option>
          <option value="price,asc">Precio: menor a mayor</option>
          <option value="price,desc">Precio: mayor a menor</option>
          <option value="newest,desc">Más recientes</option>
        </select>
        <label className="check">
          <input type="checkbox" checked={inStock} onChange={(e) => update({ disponibles: e.target.checked ? '1' : null })} />
          Solo disponibles
        </label>
      </section>

      <div className="chips" role="tablist" aria-label="Categorías">
        <button className={`chip ${!category ? 'active' : ''}`} onClick={() => update({ categoria: null })}>Todo</button>
        {categories.data?.map((c) => (
          <button key={c.id} className={`chip ${category === c.slug ? 'active' : ''}`} onClick={() => update({ categoria: c.slug })}>
            {c.name} <span className="chip-count">{c.productCount}</span>
          </button>
        ))}
      </div>

      {error && <Alert>{error}</Alert>}
      {products.error && <Alert>{products.error}</Alert>}
      {products.loading && !products.data ? <Loading /> : products.data && (
        products.data.content.length === 0 ? (
          <Empty icon={<PackageSearch size={36} />} title="Sin resultados">
            <p>Prueba con otra búsqueda o quita filtros.</p>
          </Empty>
        ) : (
          <div className={`product-grid ${products.loading ? 'is-loading' : ''}`}>
            {products.data.content.map((p) => (
              <ProductCard key={p.id} product={p} onAdded={(m) => { setError(null); setToast(m); }} onError={setError} />
            ))}
          </div>
        )
      )}
      {products.data && (
        <Pagination page={products.data.page} totalPages={products.data.totalPages}
          totalElements={products.data.totalElements} size={PAGE_SIZE}
          onPage={(p) => { update({ pagina: p ? String(p) : null }); window.scrollTo({ top: 0, behavior: 'smooth' }); }} />
      )}
      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </>
  );
}

function ProductCard({ product: p, onAdded, onError }:
  { product: Product; onAdded: (msg: string) => void; onError: (msg: string) => void }) {
  const { user, can } = useAuth();
  const { add } = useCart();
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const soldOut = p.availability === 'OUT_OF_STOCK';

  async function addToCart() {
    if (!user) { navigate('/login', { state: { from: `/productos/${p.id}` } }); return; }
    setBusy(true);
    try {
      await add(p.id, 1);
      onAdded(`${p.name} se agregó al carrito`);
    } catch (e) {
      onError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="product-card">
      <Link to={`/productos/${p.id}`} className="product-card-media">
        <ProductImage src={p.imageUrl} alt={p.name} category={p.category.slug} />
      </Link>
      <div className="product-card-body">
        <span className="product-category">{p.category.name}</span>
        <Link to={`/productos/${p.id}`} className="product-name">{p.name}</Link>
        <div className="product-card-footer">
          <div>
            <div className="price">{fmtMoney(p.price)}</div>
            <AvailabilityBadge availability={p.availability} stock={p.stock} />
          </div>
          {(!user || can('CUSTOMER')) && (
            <button className="btn btn-primary btn-sm" disabled={soldOut || busy} onClick={addToCart}>
              {soldOut ? 'Agotado' : busy ? 'Agregando…' : 'Agregar'}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
