import { useEffect, useState, type ReactNode } from 'react';
import {
  ChevronLeft, ChevronRight, X, Minus, Plus, Headphones, Footprints, Watch, Laptop, Coffee, Package,
} from 'lucide-react';
import type { Availability, OrderStatus } from '../api/types';
import { STATUS_LABEL, STATUS_TONE, fmtNumber } from '../lib/format';

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className = '' }:
  { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge-${STATUS_TONE[status]}`}><span className="badge-dot" />{STATUS_LABEL[status]}</span>;
}

export function AvailabilityBadge({ availability, stock }: { availability: Availability; stock: number }) {
  if (availability === 'OUT_OF_STOCK') return <span className="badge badge-red">Agotado</span>;
  if (availability === 'LOW_STOCK') return <span className="badge badge-amber">¡Solo quedan {stock}!</span>;
  return <span className="badge badge-green">Disponible</span>;
}

const CATEGORY_ART: Record<string, { icon: typeof Package; tone: string }> = {
  audio: { icon: Headphones, tone: 'violet' },
  calzado: { icon: Footprints, tone: 'orange' },
  relojes: { icon: Watch, tone: 'teal' },
  computacion: { icon: Laptop, tone: 'blue' },
  hogar: { icon: Coffee, tone: 'amber' },
};

/** Imagen del producto; si no tiene URL (o falla), muestra una ilustración por categoría. */
export function ProductImage({ src, alt, category, size = 'md' }:
  { src?: string; alt: string; category: string; size?: 'sm' | 'md' | 'lg' }) {
  const [failed, setFailed] = useState(false);
  const art = CATEGORY_ART[category] ?? { icon: Package, tone: 'gray' };
  const Icon = art.icon;
  if (src && !failed) {
    return <div className={`product-img product-img-${size}`}><img src={src} alt={alt} onError={() => setFailed(true)} /></div>;
  }
  return (
    <div className={`product-img product-img-${size} art-${art.tone}`} role="img" aria-label={alt}>
      <Icon strokeWidth={1.4} />
    </div>
  );
}

export function QtyStepper({ value, max, onChange, disabled }:
  { value: number; max: number; onChange: (v: number) => void; disabled?: boolean }) {
  return (
    <div className="stepper">
      <button type="button" className="stepper-btn" onClick={() => onChange(value - 1)} disabled={disabled || value <= 1}
        aria-label="Quitar uno"><Minus size={14} /></button>
      <span className="stepper-value" aria-live="polite">{value}</span>
      <button type="button" className="stepper-btn" onClick={() => onChange(value + 1)} disabled={disabled || value >= max}
        aria-label="Agregar uno"><Plus size={14} /></button>
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, size, onPage }:
  { page: number; totalPages: number; totalElements: number; size: number; onPage: (p: number) => void }) {
  if (totalElements === 0 || totalPages <= 1) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <div className="pagination">
      <span className="muted">{fmtNumber(from)}–{fmtNumber(to)} de {fmtNumber(totalElements)}</span>
      <div className="pagination-buttons">
        <button className="btn btn-ghost btn-icon" disabled={page === 0} onClick={() => onPage(page - 1)}
          aria-label="Página anterior"><ChevronLeft size={16} /></button>
        <span className="page-indicator">{page + 1} / {Math.max(totalPages, 1)}</span>
        <button className="btn btn-ghost btn-icon" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}
          aria-label="Página siguiente"><ChevronRight size={16} /></button>
      </div>
    </div>
  );
}

export function Modal({ title, onClose, children, width = 520 }:
  { title: string; onClose: () => void; children: ReactNode; width?: number }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" style={{ maxWidth: width }} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="btn btn-ghost btn-icon" onClick={onClose} aria-label="Cerrar"><X size={18} /></button>
        </header>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, error, hint, children, required }:
  { label: string; error?: string; hint?: string; children: ReactNode; required?: boolean }) {
  return (
    <label className={`field ${error ? 'has-error' : ''}`}>
      <span className="field-label">{label}{required && <span className="req"> *</span>}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info' | 'warning'; children: ReactNode }) {
  return <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Empty({ icon, title, children }: { icon?: ReactNode; title?: string; children?: ReactNode }) {
  return (
    <div className="empty">
      {icon && <div className="empty-icon">{icon}</div>}
      {title && <h3>{title}</h3>}
      {children}
    </div>
  );
}

export function Loading({ label = 'Cargando…' }: { label?: string }) {
  return <div className="loading"><span className="spinner" /> {label}</div>;
}

export function Toast({ message, onDone }: { message: string; onDone: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDone, 2600);
    return () => clearTimeout(t);
  }, [message, onDone]);
  return <div className="toast" role="status">{message}</div>;
}
