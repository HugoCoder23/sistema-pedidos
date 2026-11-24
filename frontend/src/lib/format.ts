import type { OrderStatus, PaymentStatus, Role } from '../api/types';

const money = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
const number = new Intl.NumberFormat('es-MX');
const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });
const dayShort = new Intl.DateTimeFormat('es-MX', { day: '2-digit', month: 'short' });

export const fmtMoney = (v: number | null | undefined) => money.format(v ?? 0);
export const fmtNumber = (v: number | null | undefined) => number.format(v ?? 0);
export const fmtDateTime = (iso: string) => dateTime.format(new Date(iso));
export const fmtDate = (iso: string) => dateOnly.format(new Date(iso));
/** yyyy-MM-dd sin desfasar por zona horaria. */
export const fmtDay = (ymd: string) => {
  const [y, m, d] = ymd.split('-').map(Number);
  return dayShort.format(new Date(y, m - 1, d));
};

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Pendiente de pago',
  PAID: 'Pagado',
  PROCESSING: 'En preparación',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregado',
  CANCELLED: 'Cancelado',
};

/** Texto del botón para mover un pedido a ese estado. */
export const STATUS_ACTION: Record<OrderStatus, string> = {
  PENDING: 'Marcar pendiente',
  PAID: 'Marcar pagado',
  PROCESSING: 'Iniciar preparación',
  SHIPPED: 'Marcar enviado',
  DELIVERED: 'Marcar entregado',
  CANCELLED: 'Cancelar pedido',
};

export const STATUS_TONE: Record<OrderStatus, string> = {
  PENDING: 'amber',
  PAID: 'blue',
  PROCESSING: 'violet',
  SHIPPED: 'teal',
  DELIVERED: 'green',
  CANCELLED: 'gray',
};

export const PAYMENT_LABEL: Record<PaymentStatus, string> = {
  APPROVED: 'Aprobado',
  DECLINED: 'Rechazado',
  REFUNDED: 'Reembolsado',
};

export const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: 'Cliente',
  OPERATOR: 'Operador',
  ADMIN: 'Administrador',
};

export const ORDER_FLOW: OrderStatus[] = ['PENDING', 'PAID', 'PROCESSING', 'SHIPPED', 'DELIVERED'];
