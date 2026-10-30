import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import { ListSkeleton } from '../components/Skeleton';
import { AUTO_CART_STATUS, formatDateTime, formatWon } from '../labels';

const WANT = 10;
const FETCH_SIZE = 100;

// 서버 목록에는 담기 전 미리보기(PREVIEWED)도 들어 있다. 레시피를 열 때마다 생기는 임시 기록이라
// 담은 기록(CONFIRMED)만 보여 주되, 서버에 상태로 거르는 옵션이 없어서 받아 가며 걸러낸다.
// 담은 기록이 WANT개 모이면 거기서 멈추고, 다음 쪽 번호(없으면 null)를 돌려줘 "더 보기"로 이어 받는다
async function fetchConfirmed(startPage, isCanceled) {
  let list = [];
  let page = startPage;
  while (list.length < WANT) {
    if (isCanceled()) return { list, nextPage: page };
    const data = await api.get('/api/auto-carts', { page, size: FETCH_SIZE });
    list = list.concat(data.content.filter((item) => item.status === 'CONFIRMED'));
    page += 1;
    if (page >= data.totalPages) return { list, nextPage: null };
  }
  return { list, nextPage: page };
}

// 자동 담기로 장바구니에 담은 기록. 누르면 그 기록을 다시 볼 수 있다
export default function AutoCartHistory() {
  const [items, setItems] = useState(null);
  const [nextPage, setNextPage] = useState(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    fetchConfirmed(0, () => ignore)
      .then(({ list, nextPage: next }) => {
        if (ignore) return;
        setItems(list);
        setNextPage(next);
      })
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, []);

  const loadMore = async () => {
    setError(null);
    setLoadingMore(true);
    try {
      const { list, nextPage: next } = await fetchConfirmed(nextPage, () => false);
      setItems((prev) => [...prev, ...list]);
      setNextPage(next);
    } catch (e) {
      setError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <div className="container page">
      <h1 className="page__title">자동 담기 기록</h1>
      <ErrorNotice error={error} />
      {!items && !error && <ListSkeleton />}
      {items?.length === 0 && nextPage === null && (
        <EmptyState
          icon="recipe"
          title="자동 담기 기록이 없어요"
          hint="레시피에서 재료를 장바구니에 담으면 여기에 남아요."
          actions={[{ to: '/recipes', label: '레시피 보러 가기' }]}
        />
      )}
      {items?.length > 0 && (
        <section className="card">
          {items.map((item) => (
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
      {items && nextPage !== null && (
        <div className="row-actions" style={{ justifyContent: 'center', marginTop: 16 }}>
          <button type="button" className="btn btn--outline" disabled={loadingMore} onClick={loadMore}>
            {loadingMore ? '불러오는 중...' : '더 보기'}
          </button>
        </div>
      )}
    </div>
  );
}
