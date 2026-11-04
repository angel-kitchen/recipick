import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { PAYMENT_STATUS, formatDateTime, formatPoint, formatWon } from '../labels';

export default function AccountHistory({ kind }) {
    const [page, setPage] = useState(0);
    const [data, setData] = useState(null);
    const [error, setError] = useState(null);
    const payments = kind === 'payments';
    useEffect(() => {
        let ignore = false;
        setError(null);
        setData(null);
        api.get(payments ? '/api/payments' : '/api/points/earnings', { page, size: 20 })
            .then((value) => !ignore && setData(value))
            .catch((e) => !ignore && setError(e));
        return () => {
            ignore = true;
        };
    }, [payments, page]);
    return (
        <div className="container page">
            <h1 className="page__title">{payments ? '내 결제 내역' : '포인트 적립 내역'}</h1>
            <ErrorNotice error={error} />
            <div className="card table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>주문</th>
                            <th>{payments ? '결제 금액' : '최초 적립'}</th>
                            <th>상태</th>
                            <th>{payments ? '결제 일시' : '사용 가능 잔량'}</th>
                            <th>{payments ? '환불' : '만료일'}</th>
                        </tr>
                    </thead>
                    <tbody>
                        {data?.content.map((item) => (
                            <tr key={payments ? item.paymentId : item.earningId}>
                                <td>
                                    <Link to={`/orders/${item.orderId}`}>{item.orderId}</Link>
                                </td>
                                <td>{payments ? formatWon(item.amount) : formatPoint(item.amount)}</td>
                                <td>
                                    {payments
                                        ? (PAYMENT_STATUS[item.paymentStatus] ?? item.paymentStatus)
                                        : ({
                                              PENDING: '적립 예정',
                                              COMPLETED: '적립 완료',
                                              CANCELED: '적립 취소',
                                              EXPIRED: '만료',
                                              FORFEITED: '탈퇴 소멸',
                                          }[item.status] ?? item.status)}
                                </td>
                                <td>
                                    {payments
                                        ? formatDateTime(item.paidAt)
                                        : formatPoint(item.availableRemainingAmount)}
                                </td>
                                <td>
                                    {payments
                                        ? `${formatWon(item.completedRefundAmount ?? 0)} · ${item.latestRefundStatus ?? '-'}`
                                        : (item.expiresOn ?? '-')}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {data?.content.length === 0 && <p className="state-message">내역이 없어요.</p>}
            </div>
            {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
        </div>
    );
}
