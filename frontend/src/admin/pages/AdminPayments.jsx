import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { PAYMENT_STATUS, formatDateTime, formatWon } from '../../labels';
import useLoad from '../useLoad';

function PaymentDetail({ paymentId }) {
  const { data, error } = useLoad(() => api.get(`/api/admin/payments/${paymentId}`), [paymentId]);
  if (error) return <ErrorNotice error={error} />;
  if (!data) return <p className="state-message">불러오는 중입니다.</p>;
  return (
    <section className="card admin__section">
      <h2 className="tile__title">결제 {paymentId} 상세</h2>
      <pre className="json">{JSON.stringify(data, null, 2)}</pre>
    </section>
  );
}

export default function AdminPayments() {
  const [filter, setFilter] = useState({ status: '', memberId: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const { data, error } = useLoad(() => api.get('/api/admin/payments', { ...filter, page, size: 20 }), [filter, page]);

  const setFilterValue = (key) => (e) => {
    setPage(0);
    setFilter((prev) => ({ ...prev, [key]: e.target.value }));
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">결제</h1>
      </div>
      <div className="filters">
        <select className="field__input" value={filter.status} onChange={setFilterValue('status')}>
          <option value="">상태 전체</option>
          {Object.entries(PAYMENT_STATUS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <input className="field__input" inputMode="numeric" placeholder="회원 ID" value={filter.memberId} onChange={setFilterValue('memberId')} />
        <input className="field__input" type="date" value={filter.from} onChange={setFilterValue('from')} />
        <input className="field__input" type="date" value={filter.to} onChange={setFilterValue('to')} />
      </div>
      <ErrorNotice error={error} />
      {selected && <PaymentDetail key={selected} paymentId={selected} />}
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>결제 ID</th>
              <th>주문 ID</th>
              <th>회원 ID</th>
              <th>금액</th>
              <th>상태</th>
              <th>결제 시각</th>
              <th>환불 금액</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((payment) => (
              <tr key={payment.paymentId} className="is-clickable" onClick={() => setSelected(payment.paymentId)}>
                <td>{payment.paymentId}</td>
                <td>{payment.orderId}</td>
                <td>{payment.memberId}</td>
                <td>{formatWon(payment.amount)}</td>
                <td>{PAYMENT_STATUS[payment.paymentStatus] ?? payment.paymentStatus}</td>
                <td>{formatDateTime(payment.paidAt)}</td>
                <td>{formatWon(payment.refundedAmount ?? 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">결제 내역이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
