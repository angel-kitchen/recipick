import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { ORDER_MART_STATUS, ORDER_STATUS, formatDateTime, formatWon } from '../../labels';
import { useAdmin } from '../AdminContext';
import useLoad from '../useLoad';

function OrderDetail({ orderId, onChanged }) {
  const { data: order, error, reload } = useLoad(() => api.get(`/api/admin/orders/${orderId}`), [orderId]);
  const [reason, setReason] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [message, setMessage] = useState(null);

  const run = async (action, done) => {
    setActionError(null);
    setMessage(null);
    try {
      await action();
      setMessage(done);
      setRejecting(false);
      setReason('');
      reload();
      onChanged();
    } catch (e) {
      setActionError(e);
    }
  };

  if (error) return <ErrorNotice error={error} />;
  if (!order) return <p className="state-message">불러오는 중입니다.</p>;

  return (
    <section className="card admin__section">
      <h2 className="tile__title">
        주문 {order.orderNumber} · {ORDER_STATUS[order.status] ?? order.status} · {ORDER_MART_STATUS[order.martStatus]}
      </h2>
      <ErrorNotice error={actionError} />
      {message && <div className="notice notice--info">{message}</div>}
      <dl className="kv" style={{ margin: '12px 0' }}>
        <dt>받는 분</dt>
        <dd>
          {order.receiverName} ({order.receiverPhone})
        </dd>
        <dt>주소</dt>
        <dd>
          {order.address} {order.detailAddress}
        </dd>
        <dt>금액</dt>
        <dd>
          상품 {formatWon(order.productAmount)} + 배송비 {formatWon(order.deliveryFee)} = {formatWon(order.payAmount)}
        </dd>
        {order.cancelReasonDetail && (
          <>
            <dt>취소·거절 사유</dt>
            <dd>{order.cancelReasonDetail}</dd>
          </>
        )}
      </dl>
      <ul className="list-plain">
        {order.items.map((item) => (
          <li key={item.productId}>
            <span>
              {item.productName} × {item.quantity}
            </span>
            <span>{formatWon(item.lineAmount)}</span>
          </li>
        ))}
      </ul>
      <div className="row-actions">
        {order.martStatus === 'RECEIVED' && (
          <button
            type="button"
            className="btn"
            onClick={() => run(() => api.post(`/api/admin/orders/${orderId}/accept`), '주문을 수락했습니다.')}
          >
            수락
          </button>
        )}
        {['RECEIVED', 'PREPARING'].includes(order.martStatus) && !rejecting && (
          <button type="button" className="btn btn--outline" onClick={() => setRejecting(true)}>
            거절
          </button>
        )}
      </div>
      {rejecting && (
        <div className="stack" style={{ marginTop: 12 }}>
          <label className="field">
            <span className="field__label">거절 사유 (결제가 취소됩니다)</span>
            <input className="field__input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
          </label>
          <div className="row-actions" style={{ marginTop: 0 }}>
            <button
              type="button"
              className="btn"
              disabled={!reason.trim()}
              onClick={() =>
                run(
                  () => api.post(`/api/admin/orders/${orderId}/reject`, { reason }, { 'Idempotency-Key': crypto.randomUUID() }),
                  '주문을 거절했습니다.',
                )
              }
            >
              거절 확정
            </button>
            <button type="button" className="btn btn--outline" onClick={() => setRejecting(false)}>
              취소
            </button>
          </div>
        </div>
      )}
      {order.histories?.length > 0 && (
        <details style={{ marginTop: 12 }}>
          <summary>상태 이력</summary>
          <pre className="json">{JSON.stringify(order.histories, null, 2)}</pre>
        </details>
      )}
    </section>
  );
}

export default function AdminOrders() {
  const { admin } = useAdmin();
  const [filter, setFilter] = useState({ martStatus: '', martId: '' });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const { data, error, loading, reload } = useLoad(
    () => api.get('/api/admin/orders', { ...filter, page, size: 20 }),
    [filter, page],
  );

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">주문</h1>
      </div>
      <div className="filters">
        <select
          className="field__input"
          value={filter.martStatus}
          onChange={(e) => {
            setPage(0);
            setFilter((prev) => ({ ...prev, martStatus: e.target.value }));
          }}
        >
          <option value="">마트 처리 상태 전체</option>
          {Object.entries(ORDER_MART_STATUS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        {admin.role === 'SUPER_ADMIN' && (
          <input
            className="field__input"
            inputMode="numeric"
            placeholder="마트 ID"
            value={filter.martId}
            onChange={(e) => {
              setPage(0);
              setFilter((prev) => ({ ...prev, martId: e.target.value }));
            }}
          />
        )}
      </div>
      <ErrorNotice error={error} />
      {selected && <OrderDetail orderId={selected} onChanged={reload} />}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>주문번호</th>
              <th>주문 시각</th>
              <th>상품</th>
              <th>결제 상태</th>
              <th>마트 처리</th>
              <th>금액</th>
              <th>받는 분</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((order) => (
              <tr key={order.orderId} className="is-clickable" onClick={() => setSelected(order.orderId)}>
                <td>{order.orderNumber}</td>
                <td>{formatDateTime(order.createdAt)}</td>
                <td>{order.orderName}</td>
                <td>{ORDER_STATUS[order.status] ?? order.status}</td>
                <td>{ORDER_MART_STATUS[order.martStatus]}</td>
                <td>{formatWon(order.payAmount)}</td>
                <td>{order.receiverName}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">주문이 없습니다.</p>}
        {loading && !data && <p className="state-message">불러오는 중입니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
