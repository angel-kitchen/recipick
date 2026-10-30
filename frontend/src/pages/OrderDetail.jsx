import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { cancelPendingOrder } from '../api/orders';
import ErrorNotice from '../components/ErrorNotice';
import { ORDER_STATUS, formatWon } from '../labels';

export default function OrderDetail() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);

  const [cancelError, setCancelError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const load = useCallback(() => {
    api
      .get(`/api/orders/${orderId}`)
      .then(setOrder)
      .catch(setError);
  }, [orderId]);

  useEffect(() => {
    load();
  }, [load]);

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

  if (error) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
      </div>
    );
  }
  if (!order) return <p className="state-message">불러오는 중입니다.</p>;

  return (
    <div className="container page">
      <h1 className="page__title">{order.status === 'PENDING_PAYMENT' ? '주문이 생성되었습니다' : '주문 정보'}</h1>
      {order.status === 'PENDING_PAYMENT' && (
        <div className="notice notice--info">
          결제 대기 상태입니다. 결제 화면은 아직 연결되지 않았습니다. 이 주문이 남아 있으면 새 주문을 만들 수 없으니, 쓰지 않을 주문은 취소해 주세요.
          <ErrorNotice error={cancelError} />
          <div className="row-actions">
            <button type="button" className="btn" disabled={cancelling} onClick={cancel}>
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
        <div className="summary__row summary__total">
          <span>결제 금액</span>
          <span>{formatWon(order.payAmount)}</span>
        </div>
        <p className="field__hint" style={{ marginTop: 16 }}>
          {order.receiverName} · {order.receiverPhone}
          <br />
          {order.address} {order.detailAddress}
        </p>
      </section>
      <p style={{ marginTop: 24 }}>
        <Link to="/recipes" className="btn btn--outline">
          레시피 더 보기
        </Link>
      </p>
    </div>
  );
}
