import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { ListSkeleton } from '../components/Skeleton';
import { AUTO_CART_STATUS, formatDateTime, formatWon } from '../labels';

const PAGE_SIZE = 10;

// 자동 담기 미리보기를 만든 기록. 누르면 그 미리보기를 다시 열어 담을 수 있다
export default function AutoCartHistory() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    api
      .get('/api/auto-carts', { page, size: PAGE_SIZE })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [page]);

  return (
    <div className="container page">
      <h1 className="page__title">자동 담기 기록</h1>
      <ErrorNotice error={error} />
      {!result && !error && <ListSkeleton />}
      {result?.content.length === 0 && (
        <EmptyState
          icon="recipe"
          title="자동 담기 기록이 없어요"
          hint="레시피에서 재료 미리보기를 만들면 여기에 남아요."
          actions={[{ to: '/recipes', label: '레시피 보러 가기' }]}
        />
      )}
      {result?.content.length > 0 && (
        <section className="card">
          {result.content.map((item) => (
            <Link key={item.autoCartRequestId} to={`/auto-carts/${item.autoCartRequestId}`} className="order-row">
              <span>
                <span className="cart-row__name">{item.menuSummary}</span>
                <span className="tile__meta" style={{ display: 'block' }}>
                  {formatDateTime(item.createdAt)} · {item.martName}
                </span>
              </span>
              <span style={{ textAlign: 'right' }}>
                <span className="tile__price">{formatWon(item.totalPrice)}</span>
                <span className="badge" style={{ display: 'inline-block' }}>
                  {AUTO_CART_STATUS[item.status] ?? item.status}
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
