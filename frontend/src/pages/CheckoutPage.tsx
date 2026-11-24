import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { ApiError, api } from '../api/client';
import type { Address, Order } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { fmtMoney } from '../lib/format';
import { Alert, Card, Field, Loading, PageHeader } from '../components/ui';
import { Totals } from '../components/OrderParts';

const EMPTY: Address = { recipient: '', phone: '', line1: '', line2: '', city: '', state: '', postalCode: '', instructions: '' };

export function CheckoutPage() {
  const { user } = useAuth();
  const { cart, refresh, reset } = useCart();
  const navigate = useNavigate();
  const [address, setAddress] = useState<Address>({ ...EMPTY, recipient: user?.name ?? '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!cart) return <Loading />;
  if (cart.items.length === 0 && !busy) return <Navigate to="/carrito" replace />;

  const set = (k: keyof Address) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setAddress({ ...address, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setErrors({});
    try {
      // Solo se envía la dirección: el backend relee precios y stock y calcula los importes.
      const order = await api.post<Order>('/api/orders', { shippingAddress: address });
      reset();
      navigate(`/pedidos/${order.id}/pago`, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(Object.fromEntries(Object.entries(err.fieldErrors).map(([k, v]) => [k.replace('shippingAddress.', ''), v])));
        setError(err.message);
        if (err.status === 422) refresh().catch(() => {});
      } else setError('No se pudo crear el pedido');
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Checkout" subtitle="Dirección de entrega y confirmación" />
      <form className="two-col" onSubmit={submit} noValidate>
        <Card title="Dirección de entrega">
          {error && <Alert>{error} {error.includes('carrito') || error.includes('quedan') || error.includes('agot')
            ? <Link to="/carrito" className="link">Revisar carrito</Link> : null}</Alert>}
          <div className="form">
            <div className="grid-2">
              <Field label="Nombre de quien recibe" required error={errors.recipient}>
                <input value={address.recipient} onChange={set('recipient')} autoComplete="name" />
              </Field>
              <Field label="Teléfono" required error={errors.phone} hint="10 dígitos">
                <input value={address.phone} onChange={set('phone')} autoComplete="tel" inputMode="tel" />
              </Field>
            </div>
            <Field label="Calle y número" required error={errors.line1}>
              <input value={address.line1} onChange={set('line1')} autoComplete="address-line1" />
            </Field>
            <Field label="Colonia / referencia" error={errors.line2}>
              <input value={address.line2} onChange={set('line2')} autoComplete="address-line2" />
            </Field>
            <div className="grid-3">
              <Field label="Ciudad" required error={errors.city}><input value={address.city} onChange={set('city')} autoComplete="address-level2" /></Field>
              <Field label="Estado" required error={errors.state}><input value={address.state} onChange={set('state')} autoComplete="address-level1" /></Field>
              <Field label="Código postal" required error={errors.postalCode}>
                <input value={address.postalCode} onChange={set('postalCode')} autoComplete="postal-code" inputMode="numeric" maxLength={5} />
              </Field>
            </div>
            <Field label="Indicaciones para la entrega" error={errors.instructions}>
              <textarea rows={2} value={address.instructions} onChange={set('instructions')} />
            </Field>
            <p className="muted small">Usa una dirección ficticia: es una tienda de demostración.</p>
          </div>
        </Card>
        <Card title="Resumen del pedido" className="summary-card">
          <ul className="mini-lines">
            {cart.items.map((i) => (
              <li key={i.id}><span>{i.quantity} × {i.name}</span><span>{fmtMoney(i.lineTotal)}</span></li>
            ))}
          </ul>
          <Totals subtotal={cart.subtotal} shipping={cart.shippingCost} total={cart.total} />
          <button className="btn btn-primary btn-block" disabled={busy || !cart.canCheckout}>
            <Lock size={16} /> {busy ? 'Confirmando…' : `Confirmar pedido · ${fmtMoney(cart.total)}`}
          </button>
          <p className="muted small center">Al confirmar reservamos tus artículos. Después eliges cómo pagar.</p>
        </Card>
      </form>
    </>
  );
}
