import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { formatDateTime, formatPoint, POINT_HISTORY_TYPE } from '../../labels';
import useLoad from '../useLoad';

function PointDetails({ memberId }) {
    const [page, setPage] = useState(0);
    const [orderId, setOrderId] = useState('');
    const { data: balance, error: balanceError } = useLoad(
        () => api.get(`/api/admin/members/${memberId}/points`),
        [memberId],
    );
    const { data, error } = useLoad(
        () => api.get(`/api/admin/members/${memberId}/points/history`, { orderId, page, size: 20 }),
        [memberId, orderId, page],
    );
    return (
        <>
            <ErrorNotice error={balanceError ?? error} />
            {balance && (
                <p>
                    회원 {memberId} · 사용 가능 {formatPoint(balance.availableBalance)} · 만료 예정{' '}
                    {formatPoint(balance.nearestExpiringAmount)} ({balance.nearestExpiresOn ?? '-'})
                </p>
            )}
            <input
                className="field__input"
                type="number"
                min="1"
                placeholder="주문 ID로 이력 검색"
                value={orderId}
                onChange={(e) => {
                    setOrderId(e.target.value);
                    setPage(0);
                }}
            />
            <div className="card table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>적립 건</th>
                            <th>주문</th>
                            <th>유형</th>
                            <th>포인트</th>
                            <th>시각</th>
                        </tr>
                    </thead>
                    <tbody>
                        {data?.content.map((item) => (
                            <tr key={item.historyId}>
                                <td>{item.earningId}</td>
                                <td>{item.orderId ?? '-'}</td>
                                <td>{POINT_HISTORY_TYPE[item.type] ?? item.type}</td>
                                <td>{formatPoint(item.amount)}</td>
                                <td>{formatDateTime(item.createdAt)}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {data?.content.length === 0 && <p className="state-message">포인트 이력이 없습니다.</p>}
            </div>
            {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
        </>
    );
}

export default function AdminMemberPoints() {
    const [input, setInput] = useState('');
    const [memberId, setMemberId] = useState(null);
    return (
        <>
            <div className="admin__head">
                <h1 className="page__title">회원 포인트</h1>
            </div>
            <form
                className="filters"
                onSubmit={(e) => {
                    e.preventDefault();
                    setMemberId(Number(input));
                }}
            >
                <input
                    className="field__input"
                    type="number"
                    min="1"
                    required
                    placeholder="회원 ID"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                />
                <button className="btn">조회</button>
            </form>
            {memberId && <PointDetails key={memberId} memberId={memberId} />}
        </>
    );
}
