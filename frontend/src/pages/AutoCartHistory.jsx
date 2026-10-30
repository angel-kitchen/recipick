import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { ListSkeleton } from '../components/Skeleton';
import { AUTO_CART_STATUS, formatDateTime, formatWon } from '../labels';

const PAGE_SIZE = 10;
const FETCH_SIZE = 100;

// 서버 목록에는 담기 전 미리보기(PREVIEWED)도 들어 있다. 레시피를 열 때마다 생기는 임시 기록이라
// 담은 기록(CONFIRMED)만 모아 보여 주고, 쪽 나누기는 걸러낸 뒤에 한다
async function fetchConfirmed(isCanceled) {
  let list = [];
  for (let page = 0; ; page += 1) {
    if (isCanceled()) return list;
    const data = await api.get('/api/auto-carts', { page, size: FETCH_SIZE });
    list = list.concat(data.content.filter((item) => item.status === 'CONFIRMED'));
    if (page + 1 >= data.totalPages) return list;
  }
}

// 자동 담기로 장바구니에 담은 기록. 누르면 그 기록을 다시 볼 수 있다
export default function AutoCartHistory() {
  const [page, setPage] = useState(0);
  const [all, setAll] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchConfirmed(() => ignore)
      .then((list) => !ignore && setAll(list))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, []);

  const totalPages = all ? Math.ceil(all.length / PAGE_SIZE) : 0;
  const content = all ? all.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE) : [];

  return (
    <div className="container page">
      <h1 className="page__title">자동 담기 기록</h1>
      <ErrorNotice error={error} />
      {!all && !error && <ListSkeleton />}
      {all?.length === 0 && (
        <EmptyState
          icon="recipe"
          title="자동 담기 기록이 없어요"
          hint="레시피에서 재료를 장바구니에 담으면 여기에 남아요."
          actions={[{ to: '/recipes', label: '레시피 보러 가기' }]}
        />
      )}
      {content.length > 0 && (
        <section className="card">
          {content.map((item) => (
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
      {all && <Pager page={page} totalPages={totalPages} onChange={setPage} />}
    </div>
  );
}
