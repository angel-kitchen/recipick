import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { ListSkeleton } from '../components/Skeleton';
import { formatDateTime, formatPoint, POINT_HISTORY_TYPE } from '../labels';

export default function PointHistory() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    setData(null);
    setError(null);
    api
      .get('/api/points/history', { page, size: 20 })
      .then((value) => !ignore && setData(value))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [page]);

  return (
    <div className="container page">
      <h1 className="page__title">포인트 전체 이력</h1>
      <p className="page__sub">
        적립·사용·복원·소멸 내역을 최신순으로 보여드려요.
        예약과 확정은 같은 포인트의 처리 단계이므로, 이력 금액의 합계는 사용 가능 잔액과 달라요.
      </p>
      <div className="row-actions">
        <Link to="/mypage" className="btn btn--outline">내 정보</Link>
        <Link to="/points/earnings" className="btn btn--outline">포인트 적립 내역</Link>
      </div>
      <ErrorNotice error={error} />
      {!data && !error && <ListSkeleton />}
      {data && (
        <>
          <div className="card table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>유형</th>
                  <th>포인트</th>
                  <th>관련 주문</th>
                  <th>시각</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((item) => (
                  <tr key={item.historyId}>
                    <td>{POINT_HISTORY_TYPE[item.type] ?? item.type}</td>
                    <td>{formatPoint(item.amount)}</td>
                    <td>
                      {item.orderId != null
                        ? <Link to={`/orders/${item.orderId}`}>주문 {item.orderId}</Link>
                        : '-'}
                    </td>
                    <td>{formatDateTime(item.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.content.length === 0 && <p className="state-message">포인트 이력이 없어요.</p>}
          </div>
          <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
