export type Role = 'CUSTOMER' | 'OPERATOR' | 'ADMIN';
export type OrderStatus = 'PENDING' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';
export type PaymentStatus = 'APPROVED' | 'DECLINED' | 'REFUNDED';
export type Availability = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

export interface ApiErrorBody {
  status: number;
  code?: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User { id: number; name: string; email: string; role: Role }
export interface LoginResponse { token: string; tokenType: string; expiresAt: string; user: User }

export interface Category { id: number; name: string; slug: string; productCount: number }
export interface CategoryRef { id: number; name: string; slug: string }

export interface Product {
  id: number;
  sku: string;
  name: string;
  description?: string;
  price: number;
  stock: number;
  availability: Availability;
  imageUrl?: string;
  active: boolean;
  category: CategoryRef;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CartItem {
  id: number;
  productId: number;
  sku: string;
  name: string;
  imageUrl?: string;
  categorySlug: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  stock: number;
  active: boolean;
  issue?: string;
}

export interface Cart {
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  shippingCost: number;
  total: number;
  freeShippingFrom: number;
  amountForFreeShipping: number;
  canCheckout: boolean;
}

export interface Address {
  recipient: string;
  phone: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  instructions?: string;
}

export interface OrderItem { productId: number; sku: string; name: string; unitPrice: number; quantity: number; lineTotal: number }
export interface Payment {
  id: number; provider: string; reference: string; amount: number; status: PaymentStatus; failureReason?: string; createdAt: string;
}
export interface StatusChange { from?: OrderStatus; to: OrderStatus; changedAt: string; actor: string; note?: string }
export interface CustomerRef { id: number; name: string; email: string }

export interface OrderSummary {
  id: number; number: string; status: OrderStatus; createdAt: string; itemCount: number; total: number; customer: CustomerRef;
}

export interface Order {
  id: number;
  number: string;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
  customer: CustomerRef;
  shippingAddress: Address;
  items: OrderItem[];
  itemCount: number;
  subtotal: number;
  shippingCost: number;
  total: number;
  payments: Payment[];
  history: StatusChange[];
  allowedTransitions: OrderStatus[];
  canPay: boolean;
  canCancel: boolean;
  version: number;
}

export interface PaymentResult { payment: Payment; order: Order }

export interface Notification { id: number; orderId?: number; recipient: string; subject: string; body: string; createdAt: string }

export interface Report {
  days: number;
  revenue: number;
  paidOrders: number;
  averageTicket: number;
  activeProducts: number;
  ordersByStatus: Record<OrderStatus, number>;
  salesByDay: { day: string; orders: number; revenue: number }[];
  topProducts: { productId: number; name: string; units: number; revenue: number }[];
  lowStock: Product[];
}
