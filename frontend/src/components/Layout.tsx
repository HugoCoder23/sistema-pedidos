import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { ShoppingCart, LogOut, User as UserIcon, LayoutDashboard, ClipboardList, Package, Menu, X, Store } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { ROLE_LABEL } from '../lib/format';

export function Layout() {
  const { user, logout, can } = useAuth();
  const { count } = useCart();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const [lastPath, setLastPath] = useState(location.pathname);
  if (lastPath !== location.pathname) { setLastPath(location.pathname); setOpen(false); }

  const isStaff = can('ADMIN', 'OPERATOR');
  const links = [
    { to: '/', label: 'Catálogo', icon: Store, end: true, show: !isStaff },
    { to: '/cuenta', label: 'Mis pedidos', icon: UserIcon, show: can('CUSTOMER') },
    { to: '/admin', label: 'Panel', icon: LayoutDashboard, end: true, show: can('ADMIN') },
    { to: '/admin/pedidos', label: 'Pedidos', icon: ClipboardList, show: isStaff },
    { to: '/admin/productos', label: 'Productos', icon: Package, show: can('ADMIN') },
    { to: '/', label: 'Ver tienda', icon: Store, end: true, show: isStaff },
  ].filter((l) => l.show);

  return (
    <div className="app">
      <header className="topnav">
        <div className="topnav-inner">
          <Link to={isStaff ? '/admin/pedidos' : '/'} className="brand">
            <span className="brand-logo"><ShoppingCart size={18} /></span>
            <span>Tienda <strong>Nova</strong></span>
            {isStaff && <span className="brand-tag">Admin</span>}
          </Link>
          <nav className={`nav ${open ? 'open' : ''}`}>
            {links.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={label} to={to} end={end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                <Icon size={16} /> {label}
              </NavLink>
            ))}
            {user ? (
              <div className="nav-user">
                <span className="avatar" title={user.email}>{user.name.charAt(0)}</span>
                <span className="nav-user-meta">
                  <span className="nav-user-name">{user.name}</span>
                  <span className="nav-user-role">{ROLE_LABEL[user.role]}</span>
                </span>
                <button className="btn btn-ghost btn-sm" onClick={() => { logout(); navigate('/'); }}>
                  <LogOut size={14} /> Salir
                </button>
              </div>
            ) : (
              <div className="nav-user">
                <Link className="btn btn-ghost btn-sm" to="/login" state={{ from: location.pathname }}>Iniciar sesión</Link>
                <Link className="btn btn-primary btn-sm" to="/registro">Crear cuenta</Link>
              </div>
            )}
          </nav>
          {!isStaff && (
            <Link to="/carrito" className="cart-btn" aria-label={`Carrito con ${count} artículos`}>
              <ShoppingCart size={20} />
              {count > 0 && <span className="cart-count">{count > 99 ? '99+' : count}</span>}
            </Link>
          )}
          <button className="btn btn-ghost btn-icon menu-btn" onClick={() => setOpen(!open)} aria-label="Menú">
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
      <footer className="footer">
        Tienda Nova · Proyecto de portafolio con datos ficticios. Los pagos son simulados: no ingreses datos reales.
      </footer>
    </div>
  );
}
