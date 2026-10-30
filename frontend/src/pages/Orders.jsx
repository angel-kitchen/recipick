import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { ListSkeleton } from '../components/Skeleton';
import { ORDER_GROUP, ORDER_MART_STATUS, ORDER_STATUS, formatDateTime, formatWon } from '../labels';

const PAGE_SIZE = 10;

export default function Orders() {
  const [group, setGroup] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    setError(null);
    api
      .get('/api/orders', { statusGroup: group, page, size: PAGE_SIZE })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [group, page]);

  const pick = (next) => {
    setGroup(next);
    setPage(0);
  };

  return (
    <div className="container page">
      <h1 className="page__title">주문 내역</h1>
      <div className="chips" style={{ marginBottom: 16 }}>
        {Object.entries({ '': '전체', ...ORDER_GROUP }).map(([value, label]) => (
          <button key={value} type="button" className={`chip ${group === value ? 'chip--solid' : ''}`} onClick={() => pick(value)}>
            {label}
          </button>
        ))}
      </div>
      <ErrorNotice error={error} />
      {!result && !error && <ListSkeleton />}
      {result?.content.length === 0 && (
        <EmptyState
          icon="cart"
          title={group ? '이 상태의 주문이 없어요' : '아직 주문한 내역이 없어요'}
          hint="레시피를 고르면 필요한 재료를 한 번에 담을 수 있어요."
          actions={[{ to: '/recipes', label: '레시피 보러 가기' }]}
        />
      )}
      {result?.content.length > 0 && (
        <section className="card">
          {result.content.map((order) => (
            <Link key={order.orderId} to={`/orders/${order.orderId}`} className="order-row">
              <span>
                <span className="cart-row__name">{order.orderName}</span>
                <span className="tile__meta" style={{ display: 'block' }}>
                  {formatDateTime(order.createdAt)} · 주문번호 {order.orderNumber}
                </span>
              </span>
              <span style={{ textAlign: 'right' }}>
                <span className="tile__price">{formatWon(order.payAmount)}</span>
                <span style={{ display: 'block' }}>
                  <span className="badge">{ORDER_STATUS[order.status] ?? order.status}</span>{' '}
                  {order.status === 'PAID' && order.martStatus && (
                    <span className="badge">{ORDER_MART_STATUS[order.martStatus] ?? order.martStatus}</span>
                  )}
                </span>
              </span>
            </Link>
          ))}
        </section>
      )}
      {result && <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </div>
  );
}
