import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, homeFor, useAuth } from './auth/AuthContext';
import { CartProvider } from './cart/CartContext';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { Loading } from './components/ui';
import { CatalogPage } from './pages/CatalogPage';
import { ProductPage } from './pages/ProductPage';
import { CartPage } from './pages/CartPage';
import { CheckoutPage } from './pages/CheckoutPage';
import { PaymentPage } from './pages/PaymentPage';
import { SuccessPage } from './pages/SuccessPage';
import { AccountPage } from './pages/AccountPage';
import { OrderDetailPage } from './pages/OrderDetailPage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminOrdersPage } from './pages/admin/AdminOrdersPage';
import { AdminOrderPage } from './pages/admin/AdminOrderPage';
import { AdminProductsPage } from './pages/admin/AdminProductsPage';

function RequireRole({ children, roles }: { children: ReactNode; roles: Role[] }) {
  const { user, ready, can } = useAuth();
  const location = useLocation();
  if (!ready) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (!can(...roles)) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

const customer = (el: ReactNode) => <RequireRole roles={['CUSTOMER']}>{el}</RequireRole>;
const admin = (el: ReactNode) => <RequireRole roles={['ADMIN']}>{el}</RequireRole>;
const staff = (el: ReactNode) => <RequireRole roles={['ADMIN', 'OPERATOR']}>{el}</RequireRole>;

export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/registro" element={<RegisterPage />} />
            <Route element={<Layout />}>
              <Route index element={<CatalogPage />} />
              <Route path="productos/:id" element={<ProductPage />} />
              <Route path="carrito" element={<CartPage />} />
              <Route path="checkout" element={customer(<CheckoutPage />)} />
              <Route path="pedidos/:id/pago" element={customer(<PaymentPage />)} />
              <Route path="pedidos/:id/confirmacion" element={customer(<SuccessPage />)} />
              <Route path="cuenta" element={customer(<AccountPage />)} />
              <Route path="cuenta/pedidos/:id" element={customer(<OrderDetailPage />)} />
              <Route path="admin" element={admin(<AdminDashboardPage />)} />
              <Route path="admin/pedidos" element={staff(<AdminOrdersPage />)} />
              <Route path="admin/pedidos/:id" element={staff(<AdminOrderPage />)} />
              <Route path="admin/productos" element={admin(<AdminProductsPage />)} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}
