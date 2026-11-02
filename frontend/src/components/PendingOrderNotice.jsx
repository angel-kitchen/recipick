import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { cancelPendingOrder, findPendingOrder } from '../api/orders';
import { formatWon } from '../labels';
import ErrorNotice from './ErrorNotice';

// 주문을 만든 뒤 결제하지 않으면 결제 대기 주문이 남고, 그 사이에는 새 주문을 만들 수 없다.
// 장바구니를 비워도 이미 만들어진 주문은 그대로라서, 여기서 직접 취소할 수 있게 한다.
export default function PendingOrderNotice({ onChanged }) {
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const load = useCallback(() => {
    findPendingOrder()
      .then(setOrder)
      .catch(() => setOrder(null));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const cancel = async () => {
    setError(null);
    setCancelling(true);
    try {
      await cancelPendingOrder(order.orderId);
      setOrder(null);
      onChanged?.();
    } catch (e) {
      setError(e);
    } finally {
      setCancelling(false);
    }
  };

  if (!order) return null;

  return (
    <div className="notice notice--info">
      <strong>결제를 마치지 않은 주문이 있어요.</strong> {order.orderName} · {formatWon(order.payAmount)}
      <br />
      이어서 결제하거나 취소하면 새로 주문할 수 있어요.
      <ErrorNotice error={error} />
      {/* 이 안내를 보는 사람은 결제하다 떠난 경우가 많아 결제를 앞에 둔다. 결제는 주문 상세 한 곳에서만 처리한다 */}
      <div className="row-actions">
        <Link to={`/orders/${order.orderId}`} className="btn">
          결제하러 가기
        </Link>
        <button type="button" className="btn btn--outline" disabled={cancelling} onClick={cancel}>
          {cancelling ? '취소하는 중...' : '이 주문 취소하기'}
        </button>
      </div>
    </div>
  );
}
