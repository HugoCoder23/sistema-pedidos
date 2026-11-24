import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { Cart } from '../api/types';
import { useAuth } from '../auth/AuthContext';

/**
 * Estado del carrito persistente (vive en el servidor). Cada operación devuelve el carrito completo
 * recalculado por el backend, así subtotal, envío y total nunca se calculan en el navegador.
 */
interface CartState {
  cart: Cart | null;
  count: number;
  refresh: () => Promise<void>;
  add: (productId: number, quantity: number) => Promise<Cart>;
  update: (itemId: number, quantity: number) => Promise<Cart>;
  remove: (itemId: number) => Promise<Cart>;
  reset: () => void;
}

const CartContext = createContext<CartState | null>(null);

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [cart, setCart] = useState<Cart | null>(null);
  const isCustomer = user?.role === 'CUSTOMER';

  const refresh = useCallback(async () => {
    if (!isCustomer) { setCart(null); return; }
    setCart(await api.get<Cart>('/api/cart'));
  }, [isCustomer]);

  useEffect(() => { refresh().catch(() => setCart(null)); }, [refresh, user?.id]);

  const apply = useCallback((c: Cart) => { setCart(c); return c; }, []);

  const value = useMemo<CartState>(() => ({
    cart,
    count: cart?.itemCount ?? 0,
    refresh,
    add: async (productId, quantity) => apply(await api.post<Cart>('/api/cart', { productId, quantity })),
    update: async (itemId, quantity) => apply(await api.patch<Cart>(`/api/cart/items/${itemId}`, { quantity })),
    remove: async (itemId) => apply(await api.del<Cart>(`/api/cart/items/${itemId}`)),
    reset: () => setCart((c) => (c ? { ...c, items: [], itemCount: 0, subtotal: 0, shippingCost: 0, total: 0, canCheckout: false } : c)),
  }), [cart, refresh, apply]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart debe usarse dentro de CartProvider');
  return ctx;
}
