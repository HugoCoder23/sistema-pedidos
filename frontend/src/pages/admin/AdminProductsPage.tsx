import { useState, type FormEvent } from 'react';
import { Pencil, Plus, Search, PackagePlus } from 'lucide-react';
import { ApiError, api, errorMessage } from '../../api/client';
import type { Category, Page, Product } from '../../api/types';
import { useAsync, useDebounced } from '../../lib/useAsync';
import { fmtMoney } from '../../lib/format';
import { Alert, AvailabilityBadge, Card, Empty, Field, Loading, Modal, PageHeader, Pagination, ProductImage } from '../../components/ui';

const SIZE = 15;

export function AdminProductsPage() {
  const [text, setText] = useState('');
  const q = useDebounced(text.trim(), 300);
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Product | 'new' | null>(null);
  const [adjusting, setAdjusting] = useState<Product | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const categories = useAsync(() => api.get<Category[]>('/api/categories'), []);
  const list = useAsync(() => api.get<Page<Product>>('/api/admin/products', { q, category, page, size: SIZE }), [q, category, page]);

  const saved = (msg: string) => { setDone(msg); setEditing(null); setAdjusting(null); list.reload(); categories.reload(); };

  return (
    <>
      <PageHeader title="Productos e inventario" subtitle="El stock se ajusta con movimientos; editar el producto no cambia pedidos ya hechos"
        actions={<button className="btn btn-primary" onClick={() => { setDone(null); setEditing('new'); }}><Plus size={16} /> Nuevo producto</button>} />
      {done && <Alert tone="success">{done}</Alert>}
      <Card>
        <div className="filters">
          <div className="search"><Search size={16} />
            <input value={text} onChange={(e) => { setText(e.target.value); setPage(0); }} placeholder="Nombre o SKU" aria-label="Buscar productos" />
          </div>
          <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0); }} aria-label="Categoría">
            <option value="">Todas las categorías</option>
            {categories.data?.map((c) => <option key={c.id} value={c.slug}>{c.name}</option>)}
          </select>
        </div>
        {list.error && <Alert>{list.error}</Alert>}
        {list.loading && !list.data ? <Loading /> : list.data && (list.data.content.length === 0 ? <Empty title="Sin productos" /> : (
          <div className={`table-wrap ${list.loading ? 'is-loading' : ''}`}>
            <table className="table">
              <thead><tr><th>Producto</th><th>Categoría</th><th className="num">Precio</th><th className="num">Stock</th><th>Estado</th><th /></tr></thead>
              <tbody>
                {list.data.content.map((p) => (
                  <tr key={p.id} className={p.active ? '' : 'row-muted'}>
                    <td><div className="cell-product"><ProductImage src={p.imageUrl} alt={p.name} category={p.category.slug} size="sm" />
                      <div><div className="strong">{p.name}</div><div className="muted small mono">{p.sku}</div></div></div></td>
                    <td>{p.category.name}</td>
                    <td className="num">{fmtMoney(p.price)}</td>
                    <td className="num strong">{p.stock}</td>
                    <td>{p.active ? <AvailabilityBadge availability={p.availability} stock={p.stock} /> : <span className="badge badge-gray">Inactivo</span>}</td>
                    <td className="actions-col">
                      <button className="btn btn-ghost btn-sm" onClick={() => { setDone(null); setAdjusting(p); }}><PackagePlus size={14} /> Stock</button>
                      <button className="btn btn-ghost btn-icon" aria-label={`Editar ${p.name}`} onClick={() => { setDone(null); setEditing(p); }}><Pencil size={15} /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
        {list.data && <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} size={SIZE} onPage={setPage} />}
      </Card>
      {editing && <ProductForm product={editing === 'new' ? null : editing} categories={categories.data ?? []}
        onClose={() => setEditing(null)} onSaved={saved} />}
      {adjusting && <StockForm product={adjusting} onClose={() => setAdjusting(null)} onSaved={saved} />}
    </>
  );
}

function ProductForm({ product, categories, onClose, onSaved }:
  { product: Product | null; categories: Category[]; onClose: () => void; onSaved: (m: string) => void }) {
  const [form, setForm] = useState({
    sku: product?.sku ?? '', name: product?.name ?? '', description: product?.description ?? '',
    price: product ? String(product.price) : '', categoryId: product ? String(product.category.id) : String(categories[0]?.id ?? ''),
    imageUrl: product?.imageUrl ?? '', active: product?.active ?? true, initialStock: '0',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
    setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null); setErrors({});
    const body = {
      sku: form.sku, name: form.name, description: form.description, price: form.price === '' ? null : Number(form.price),
      categoryId: Number(form.categoryId), imageUrl: form.imageUrl, active: form.active,
      ...(product ? { version: product.version } : { initialStock: Number(form.initialStock || 0) }),
    };
    try {
      if (product) await api.put(`/api/admin/products/${product.id}`, body);
      else await api.post('/api/admin/products', body);
      onSaved(product ? `Se guardó "${form.name}".` : `Se creó "${form.name}".`);
    } catch (err) {
      if (err instanceof ApiError) { setErrors(err.fieldErrors); setError(err.message); } else setError('No se pudo guardar');
      setBusy(false);
    }
  }

  return (
    <Modal title={product ? 'Editar producto' : 'Nuevo producto'} onClose={onClose} width={620}>
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert>{error}</Alert>}
        <div className="grid-2">
          <Field label="SKU" required error={errors.sku}><input value={form.sku} onChange={set('sku')} /></Field>
          <Field label="Categoría" required error={errors.categoryId}>
            <select value={form.categoryId} onChange={set('categoryId')}>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
          </Field>
        </div>
        <Field label="Nombre" required error={errors.name}><input value={form.name} onChange={set('name')} /></Field>
        <Field label="Descripción" error={errors.description}><textarea rows={3} value={form.description} onChange={set('description')} /></Field>
        <div className="grid-2">
          <Field label="Precio (MXN)" required error={errors.price} hint={product ? 'Los pedidos existentes conservan su precio' : undefined}>
            <input type="number" min="0.01" step="0.01" value={form.price} onChange={set('price')} />
          </Field>
          {product ? (
            <Field label="Stock actual" hint="Usa el botón Stock para ajustarlo"><input value={product.stock} disabled /></Field>
          ) : (
            <Field label="Stock inicial" error={errors.initialStock}><input type="number" min="0" value={form.initialStock} onChange={set('initialStock')} /></Field>
          )}
        </div>
        <Field label="URL de imagen" error={errors.imageUrl} hint="Opcional. Sin imagen se muestra una ilustración por categoría">
          <input value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://…" />
        </Field>
        <label className="check"><input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Activo (visible en el catálogo)</label>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</button>
        </div>
      </form>
    </Modal>
  );
}

function StockForm({ product, onClose, onSaved }: { product: Product; onClose: () => void; onSaved: (m: string) => void }) {
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const n = Number(delta);
  const valid = delta !== '' && Number.isInteger(n) && n !== 0;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      const p = await api.patch<Product>(`/api/admin/products/${product.id}/stock`, { delta: n, reason: reason || undefined });
      onSaved(`Stock de "${p.name}" ajustado a ${p.stock} unidades.`);
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <Modal title={`Ajustar stock · ${product.name}`} onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert>{error}</Alert>}
        <div className="stock-preview">
          <div><span className="muted">Actual</span><strong>{product.stock}</strong></div>
          <div><span className="muted">Variación</span><strong>{valid ? (n > 0 ? `+${n}` : n) : '—'}</strong></div>
          <div><span className="muted">Resultado</span><strong className={valid && product.stock + n < 0 ? 'text-red' : ''}>{valid ? product.stock + n : '—'}</strong></div>
        </div>
        <Field label="Variación" hint="Positiva para entradas (+10), negativa para salidas o mermas (-2)">
          <input type="number" step="1" value={delta} onChange={(e) => setDelta(e.target.value)} autoFocus />
        </Field>
        <Field label="Motivo"><input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} placeholder="Reabastecimiento, merma…" /></Field>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn btn-primary" disabled={busy || !valid}>{busy ? 'Guardando…' : 'Aplicar ajuste'}</button>
        </div>
      </form>
    </Modal>
  );
}
