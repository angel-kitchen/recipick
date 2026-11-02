import * as PortOne from '@portone/browser-sdk/v2';
import { api } from './client';

let config = null;

// 상점·채널 키는 서버 설정을 받아 쓴다. 서버가 결제를 조회하는 API 시크릿과 같은 상점이어야 확정이 된다
function getConfig() {
  if (!config) {
    config = api.get('/api/config/portone').catch((e) => {
      config = null;
      throw e;
    });
  }
  return config;
}

// 결제창이 알려 준 성공 여부는 믿지 않는다. 서버가 PortOne에 직접 조회해 확정한 결과로만 판단한다
export async function confirmPayment(orderId, portonePaymentId) {
  try {
    await api.post('/api/payments/confirm', { orderId, portonePaymentId });
    return { status: 'PAID' };
  } catch (e) {
    // PortOne에서 승인이 아직 끝나지 않았다. 웹훅이 이어서 확정하므로 실패로 보여 주지 않는다
    if (e.code === 'PAYMENT-002') return { status: 'CHECKING' };
    // 결제창을 연 채로 만료 시각을 넘겨 승인됐다. 서버가 환불을 예약했으므로 돈이 돌아간다는 것까지 알린다
    if (e.code === 'PAYMENT-005') return { status: 'EXPIRED_AFTER_PAY' };
    throw e;
  }
}

// 결제창을 닫거나 실패로 끝나도 서버에 확정을 요청해 PortOne의 실패 기록으로 주문을 바로 닫게 한다.
// 그냥 두면 로컬은 만료까지 결제 대기로 남고 운영은 웹훅이 곧 닫아서, 그 사이 다시 결제하면 승인 뒤 환불된다
export async function confirmClosedPayment(orderId, portonePaymentId, message) {
  try {
    const result = await confirmPayment(orderId, portonePaymentId);
    // PortOne에 아직 실패 기록이 없다(결제 시작 전 오류 등). 결제 대기로 두어 다시 결제할 수 있게 한다
    return result.status === 'CHECKING' ? { status: 'CLOSED', message } : result;
  } catch (e) {
    // 실패가 확정돼 주문이 닫혔다. 웹훅이 먼저 닫았으면 이미 종료된 결제(PAYMENT-006)로 답한다
    if (e.code === 'PAYMENT-003' || e.code === 'PAYMENT-006') return { status: 'FAILED', message };
    throw e;
  }
}

// 결과 status: PAID(확정), CHECKING(승인 확인 중), EXPIRED_AFTER_PAY(만료 뒤 승인, 자동 환불),
// FAILED(결제창에서 취소·실패해 주문 종료), CLOSED(실패 기록 전이라 결제 대기 유지), REDIRECTED(모바일 페이지 이동)
export async function payOrder({ orderId, portonePaymentId, amount, orderName, customer }) {
  const { storeId, channelKey } = await getConfig();
  const response = await PortOne.requestPayment({
    storeId,
    channelKey,
    paymentId: portonePaymentId,
    orderName,
    totalAmount: amount,
    currency: 'KRW',
    payMethod: 'CARD',
    customer,
    // 서버가 PortOne 결제의 customData.orderId로 이 주문의 결제인지 확인한다. 없으면 승인돼도 확정이 거절된다
    customData: { orderId },
    // 모바일은 결제창이 페이지를 옮겨 가므로 끝나면 주문 상세로 돌아와 확정한다(OrderDetail이 쿼리를 읽는다)
    redirectUrl: `${window.location.origin}/orders/${orderId}`,
  });
  if (!response) return { status: 'REDIRECTED' };
  if (response.code) return confirmClosedPayment(orderId, portonePaymentId, response.message);
  return confirmPayment(orderId, portonePaymentId);
}

export const customerOf = (member) => ({
  fullName: member.name ?? undefined,
  phoneNumber: member.phone ?? undefined,
  email: member.email ?? undefined,
});
