import { api } from './client';

// 결제 전 취소는 결제 취소 API가 맡는다(결제 대기 주문이 닫히고 차감했던 재고가 돌아온다)
export async function cancelPendingOrder(orderId) {
  const payment = await api.get(`/api/payments/orders/${orderId}`);
  return api.post(
    `/api/payments/${payment.paymentId}/cancel`,
    { reason: '주문자가 직접 취소' },
    { 'Idempotency-Key': crypto.randomUUID() },
  );
}

// 회원당 결제 대기 주문은 최대 한 건이다
export async function findPendingOrder() {
  const page = await api.get('/api/orders', { statusGroup: 'ONGOING', size: 20 });
  return page.content.find((order) => order.status === 'PENDING_PAYMENT') ?? null;
}
