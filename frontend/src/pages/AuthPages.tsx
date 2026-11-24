import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ShoppingCart } from 'lucide-react';
import { ApiError } from '../api/client';
import { homeFor, useAuth } from '../auth/AuthContext';
import { Alert, Field } from '../components/ui';

const DEMO = [
  { label: 'Cliente', email: 'cliente@demo.local' },
  { label: 'Operador', email: 'operador@demo.local' },
  { label: 'Admin', email: 'admin@demo.local' },
];

function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <Link to="/" className="brand brand-dark">
          <span className="brand-logo"><ShoppingCart size={18} /></span>
          <span>Tienda <strong>Nova</strong></span>
        </Link>
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {children}
      </div>
    </div>
  );
}

export function LoginPage() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user)} replace />;

  async function submit(e: FormEvent, override?: { email: string; password: string }) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const creds = override ?? { email, password };
      const u = await login(creds.email, creds.password);
      const canReturn = from && !from.startsWith('/login') && (u.role === 'CUSTOMER') === !from.startsWith('/admin');
      navigate(canReturn ? from : homeFor(u), { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Inicia sesión" subtitle="Para comprar, consultar tus pedidos o entrar al panel.">
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert>{error}</Alert>}
        <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required /></Field>
        <Field label="Contraseña"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required /></Field>
        <button className="btn btn-primary btn-block" disabled={busy || !email || !password}>{busy ? 'Entrando…' : 'Entrar'}</button>
      </form>
      <p className="auth-switch">¿No tienes cuenta? <Link to="/registro" className="link">Regístrate</Link></p>
      <div className="demo-accounts">
        <p className="muted small">Cuentas de demostración (contraseña <code>Demo1234!</code>):</p>
        <div className="demo-buttons">
          {DEMO.map((d) => (
            <button key={d.email} type="button" className="btn btn-soft btn-sm" disabled={busy}
              onClick={(e) => submit(e, { email: d.email, password: 'Demo1234!' })}>
              {d.label}
            </button>
          ))}
        </div>
      </div>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { register, user } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setErrors({});
    try {
      await register(form.name, form.email, form.password);
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors);
        setError(Object.keys(err.fieldErrors).length ? null : err.message);
      } else setError('No se pudo crear la cuenta');
    } finally {
      setBusy(false);
    }
  }

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  return (
    <AuthShell title="Crea tu cuenta" subtitle="Usa un email ficticio: es una tienda de demostración.">
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert>{error}</Alert>}
        <Field label="Nombre" error={errors.name}><input value={form.name} onChange={set('name')} autoComplete="name" /></Field>
        <Field label="Email" error={errors.email}><input type="email" value={form.email} onChange={set('email')} autoComplete="email" /></Field>
        <Field label="Contraseña" error={errors.password} hint="Mínimo 8 caracteres, con letras y números">
          <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creando…' : 'Crear cuenta'}</button>
      </form>
      <p className="auth-switch">¿Ya tienes cuenta? <Link to="/login" className="link">Inicia sesión</Link></p>
    </AuthShell>
  );
}
