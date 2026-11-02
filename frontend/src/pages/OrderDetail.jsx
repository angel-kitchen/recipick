import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api, isUnknownOutcome } from '../api/client';
import { cancelPaidOrder, cancelPendingOrder } from '../api/orders';
import { confirmClosedPayment, confirmPayment, customerOf, payOrder } from '../api/payments';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import { ORDER_STATUS, formatDateTime, formatPoint, formatWon } from '../labels';

const PAYMENT_NOTICE = {
  PAID: { text: '결제가 완료되었습니다.', info: true },
  CHECKING: { text: '결제 승인을 확인하고 있어요. 잠시 후 새로고침해 주세요.', info: true },
  EXPIRED_BEFORE_PAY: { text: '결제 시간이 지났어요. 장바구니에서 다시 주문해 주세요.' },
  EXPIRED_AFTER_PAY: { text: '결제 시간이 지나 주문이 취소됐어요. 결제된 금액은 자동으로 환불돼요.' },
  FAILED: { text: '결제가 완료되지 않아 주문이 취소됐어요. 장바구니에서 다시 주문해 주세요.' },
  CLOSED: { text: '결제가 완료되지 않았어요. 아래에서 다시 결제하거나 주문을 취소할 수 있어요.' },
  ERROR: { text: '결제를 처리하지 못했어요.' },
};

// 주문당 적립 건은 하나라 적립 이력 중 가장 나중 단계가 현재 상태다. 취소된 적립은 보여 주지 않는다
const EARN_STEPS = ['EARN_PENDING', 'EARN_COMPLETED', 'EARN_CANCELED'];

async function findEarning(orderId) {
  const page = await api.get('/api/points/history', { orderId, size: 100 });
  const latest = page.content
    .filter((history) => EARN_STEPS.includes(history.type))
    .sort((a, b) => EARN_STEPS.indexOf(b.type) - EARN_STEPS.indexOf(a.type))[0];
  return latest && latest.type !== 'EARN_CANCELED' ? latest : null;
}

export default function OrderDetail() {
  const { orderId } = useParams();
  const { member } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  // 이 주문의 적립 예정·완료 이력. 못 불러와도 주문 정보는 보여 준다
  const [earning, setEarning] = useState(null);
  const [error, setError] = useState(null);
  // 주문서에서 넘어오면 결제창 결과를 들고 온다
  const [paymentResult, setPaymentResult] = useState(location.state?.payment ?? null);
  const [paying, setPaying] = useState(false);
  const redirectHandled = useRef(false);

  const [cancelError, setCancelError] = useState(null);
  const [cancelling, setCancelling] = useState(false);
  // 결제 완료 주문의 취소(환불)
  const [refundOpen, setRefundOpen] = useState(false);
  const [refunding, setRefunding] = useState(false);
  const [refundError, setRefundError] = useState(null);
  const [refundResult, setRefundResult] = useState(null);
  // 응답을 못 받은 환불을 다시 보낼 때만 같은 키를 쓴다
  const refundKey = useRef(null);

  const load = useCallback(async () => {
    try {
      const data = await api.get(`/api/orders/${orderId}`);
      setOrder(data);
      // 다시 결제할 때 쓰는 PortOne 결제 ID와 만료 시각은 결제 정보에 있다
      setPayment(data.status === 'PENDING_PAYMENT' ? await api.get(`/api/payments/orders/${orderId}`) : null);
      setEarning(await findEarning(orderId).catch(() => null));
    } catch (e) {
      setError(e);
    }
  }, [orderId]);

  // 모바일 결제창은 이 페이지로 리디렉션하며 paymentId·code·message를 쿼리로 붙인다
  useEffect(() => {
    const portonePaymentId = searchParams.get('paymentId');
    if (!portonePaymentId || redirectHandled.current) {
      load();
      return;
    }
    redirectHandled.current = true;
    const code = searchParams.get('code');
    const handle = (
      code
        ? confirmClosedPayment(Number(orderId), portonePaymentId, searchParams.get('message'))
        : confirmPayment(Number(orderId), portonePaymentId)
    ).catch((e) => ({ status: 'ERROR', message: e.message }));
    handle.then((result) => {
      setPaymentResult(result);
      // 새로고침할 때 같은 확정을 다시 보내지 않게 쿼리를 지운다
      navigate(`/orders/${orderId}`, { replace: true });
      load();
    });
  }, [load]);

  const pay = async () => {
    setPaymentResult(null);
    setPaying(true);
    try {
      // 화면을 오래 열어 두면 그 사이 만료됐을 수 있다. 만료된 결제로 결제창을 띄우면 승인 뒤 환불되므로 먼저 다시 확인한다.
      // 만료 작업이 주문을 닫기 전일 수 있어 주문 상태와 만료 시각을 함께 본다
      const [latestOrder, latestPayment] = await Promise.all([
        api.get(`/api/orders/${orderId}`),
        api.get(`/api/payments/orders/${orderId}`),
      ]);
      const expired = new Date(latestPayment.expiresAt) <= new Date();
      if (latestOrder.status !== 'PENDING_PAYMENT' || expired) {
        if (expired) setPaymentResult({ status: 'EXPIRED_BEFORE_PAY' });
        return;
      }
      setPaymentResult(
        await payOrder({
          orderId: order.orderId,
          portonePaymentId: latestPayment.portonePaymentId,
          amount: latestPayment.amount,
          orderName: order.orderName,
          customer: customerOf(member),
        }),
      );
    } catch (e) {
      setPaymentResult({ status: 'ERROR', message: e.message });
    } finally {
      setPaying(false);
      load();
    }
  };

  const cancel = async () => {
    setCancelError(null);
    setCancelling(true);
    try {
      await cancelPendingOrder(orderId);
      load();
    } catch (e) {
      setCancelError(e);
    } finally {
      setCancelling(false);
    }
  };

  const refund = async () => {
    setRefundError(null);
    setRefunding(true);
    if (!refundKey.current) refundKey.current = crypto.randomUUID();
    try {
      const result = await cancelPaidOrder(orderId, refundKey.current);
      refundKey.current = null;
      setRefundOpen(false);
      // PortOne 결과를 바로 받지 못하면(202) 환불이 REQUESTED로 남고 서버가 이어서 확인한다.
      // 0원(포인트 전액) 주문은 PG 환불 없이 즉시 끝나 refundStatus가 비어 있으므로 결제 상태로 판단한다
      const done = result.refundStatus === 'COMPLETED' || result.paymentStatus === 'CANCELED';
      setRefundResult(done ? 'DONE' : 'PENDING');
      load();
    } catch (e) {
      if (!isUnknownOutcome(e)) refundKey.current = null;
      setRefundError(e);
    } finally {
      setRefunding(false);
    }
  };

  if (error) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
      </div>
    );
  }
  if (!order) return <p className="state-message">불러오는 중입니다.</p>;

  // 결제가 끝났는데 결제 대기로 남았다는 안내는 맞지 않으므로, 결과와 현재 상태가 어긋나면 안내를 감춘다
  const notice =
    paymentResult && !(paymentResult.status !== 'PAID' && order.status === 'PAID') ? PAYMENT_NOTICE[paymentResult.status] : null;

  return (
    <div className="container page">
      <h1 className="page__title">{order.status === 'PENDING_PAYMENT' ? '주문이 생성되었습니다' : '주문 정보'}</h1>
      {notice && (
        <div className={notice.info ? 'notice notice--info' : 'notice'} role="status">
          {notice.text}
          {paymentResult.message && <br />}
          {paymentResult.message}
        </div>
      )}
      {refundResult && (
        <div className="notice notice--info" role="status">
          {refundResult === 'DONE'
            ? '주문을 취소하고 환불했어요.'
            : '환불을 처리하고 있어요. 잠시 후 다시 확인해 주세요.'}
        </div>
      )}
      {order.status === 'PENDING_PAYMENT' && (
        <div className="notice notice--info">
          결제 대기 상태입니다.
          {payment?.expiresAt && ` ${formatDateTime(payment.expiresAt)}까지 결제하지 않으면 주문이 자동으로 취소돼요.`} 이
          주문이 남아 있으면 새 주문을 만들 수 없으니, 쓰지 않을 주문은 취소해 주세요.
          <ErrorNotice error={cancelError} />
          <div className="row-actions">
            <button type="button" className="btn" disabled={paying || cancelling || !payment} onClick={pay}>
              {paying ? '결제 진행 중...' : '이어서 결제하기'}
            </button>
            <button type="button" className="btn btn--outline" disabled={paying || cancelling} onClick={cancel}>
              {cancelling ? '취소하는 중...' : '주문 취소하기'}
            </button>
          </div>
        </div>
      )}
      <section className="card">
        <p className="page__sub">
          주문번호 {order.orderNumber} · <span className="badge">{ORDER_STATUS[order.status]}</span>
        </p>
        <h2 className="tile__title">{order.orderName}</h2>
        <ul className="list-plain">
          {order.items.map((item) => (
            <li key={`${item.productId}-${item.productName}`}>
              <span>
                {item.productName} × {item.quantity}
              </span>
              <span>{formatWon(item.unitPrice * item.quantity)}</span>
            </li>
          ))}
        </ul>
        <div className="summary__row">
          <span>상품 금액</span>
          <span>{formatWon(order.productAmount)}</span>
        </div>
        <div className="summary__row">
          <span>배송비</span>
          <span>{order.deliveryFee === 0 ? '무료' : formatWon(order.deliveryFee)}</span>
        </div>
        {order.usedPoint > 0 && (
          <div className="summary__row">
            <span>포인트 사용</span>
            <span>-{formatWon(order.usedPoint)}</span>
          </div>
        )}
        <div className="summary__row summary__total">
          <span>결제 금액</span>
          <span>{formatWon(order.payAmount)}</span>
        </div>
        {earning?.type === 'EARN_PENDING' && (
          <p className="field__hint">배송이 완료되면 {formatPoint(earning.amount)}가 적립돼요.</p>
        )}
        {earning?.type === 'EARN_COMPLETED' && (
          <p className="field__hint">{formatPoint(earning.amount)}가 적립됐어요.</p>
        )}
        <p className="field__hint" style={{ marginTop: 16 }}>
          {order.receiverName} · {order.receiverPhone}
          <br />
          {order.address} {order.detailAddress}
        </p>
        {order.status === 'PAID' && order.cancelable && (
          <>
            <p className="field__hint" style={{ marginTop: 16 }}>
              마트가 주문을 접수하기 전까지 취소할 수 있어요.
            </p>
            <div className="row-actions">
              <button
                type="button"
                className="btn btn--outline"
                onClick={() => {
                  setRefundError(null);
                  setRefundOpen(true);
                }}
              >
                주문 취소하기
              </button>
            </div>
          </>
        )}
      </section>
      {refundOpen && (
        <ConfirmModal
          title="주문을 취소할까요?"
          confirmLabel="취소하고 환불받기"
          cancelLabel="닫기"
          busy={refunding}
          error={refundError}
          onConfirm={refund}
          onCancel={() => setRefundOpen(false)}
        >
          {order.payAmount > 0 && `결제한 ${formatWon(order.payAmount)}이 환불돼요. `}
          {order.usedPoint > 0 && `사용한 포인트 ${order.usedPoint.toLocaleString()}P도 돌려드려요.`}
        </ConfirmModal>
      )}
      <p style={{ marginTop: 24 }}>
        <Link to="/recipes" className="btn btn--outline">
          레시피 더 보기
        </Link>
      </p>
    </div>
  );
}
