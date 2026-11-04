import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { formatDateTime, formatWon } from '../../labels';
import useLoad from '../useLoad';

const STATUSES = {
    READY: '접수',
    DISPATCHING: '발송 중',
    UNKNOWN: '결과 확인 중',
    MANUAL_REVIEW: '운영 확인',
    PAID: '성공',
    FAILED: '실패',
    CANCELED: '취소',
};

function AttemptDetail({ attemptId, onChanged }) {
    const { data, error, reload } = useLoad(
        () => api.get(`/api/admin/membership-payment-attempts/${attemptId}`),
        [attemptId],
    );
    const [actionError, setActionError] = useState(null);
    const [result, setResult] = useState(null);
    const [busy, setBusy] = useState(false);
    const run = async (path, question) => {
        if (busy || !window.confirm(question)) return;
        setBusy(true);
        setActionError(null);
        try {
            setResult(await api.post(path));
            reload();
            onChanged();
        } catch (e) {
            setActionError(e);
        } finally {
            setBusy(false);
        }
    };
    return (
        <section className="card admin__section">
            <h2 className="tile__title">결제 시도 {attemptId} 상세</h2>
            <ErrorNotice error={error ?? actionError} />
            {data && (
                <>
                    <dl className="info-list">
                        <dt>상태</dt>
                        <dd>{STATUSES[data.attempt.status] ?? data.attempt.status}</dd>
                        <dt>회차</dt>
                        <dd>{data.attempt.membershipPaymentId}</dd>
                        <dt>이용기간</dt>
                        <dd>
                            {data.periodStartDate} ~ {data.periodEndDate}
                        </dd>
                        <dt>재시도</dt>
                        <dd>{formatDateTime(data.nextRetryAt)}</dd>
                        <dt>실패 사유</dt>
                        <dd>{data.paymentFailureReason ?? data.attempt.failureReason ?? '-'}</dd>
                        <dt>운영 확인</dt>
                        <dd>{data.attempt.opsReviewReason ?? '-'}</dd>
                        <dt>정산 차단</dt>
                        <dd>{data.attempt.settlementBlocked ? '예' : '아니오'}</dd>
                    </dl>
                    <button
                        className="btn btn--outline"
                        disabled={busy}
                        onClick={() =>
                            run(
                                `/api/admin/membership-payment-attempts/${attemptId}/recheck`,
                                'PortOne의 결제 결과를 다시 조회할까요?',
                            )
                        }
                    >
                        결제 결과 재조회
                    </button>
                    <h3>이용료 환불 이력</h3>
                    <div className="table-wrap">
                        <table className="table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>요청</th>
                                    <th>금액</th>
                                    <th>상태</th>
                                    <th>운영 확인</th>
                                    <th>접수</th>
                                    <th>처리</th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.refunds.map((refund) => (
                                    <tr key={refund.refundId}>
                                        <td>{refund.refundId}</td>
                                        <td>{refund.requestedBy === 'SYSTEM' ? '시스템' : '회원'}</td>
                                        <td>{formatWon(refund.amount)}</td>
                                        <td>{refund.status}</td>
                                        <td>{refund.opsReviewReason ?? (refund.reviewRequired ? '필요' : '-')}</td>
                                        <td>{formatDateTime(refund.requestedAt)}</td>
                                        <td>
                                            <button
                                                className="chip"
                                                disabled={busy}
                                                onClick={() =>
                                                    run(
                                                        `/api/admin/membership-refunds/${refund.refundId}/recheck`,
                                                        '환불 상태를 다시 조회할까요?',
                                                    )
                                                }
                                            >
                                                재조회
                                            </button>{' '}
                                            {refund.requestedBy === 'SYSTEM' && refund.status === 'FAILED' && (
                                                <button
                                                    className="chip"
                                                    disabled={busy}
                                                    onClick={() =>
                                                        run(
                                                            `/api/admin/membership-refunds/${refund.refundId}/retry`,
                                                            '실패한 시스템 환불을 재실행할까요? 서버가 최종 조건을 검사합니다.',
                                                        )
                                                    }
                                                >
                                                    환불 재실행
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        {data.refunds.length === 0 && <p>환불 이력이 없습니다.</p>}
                    </div>
                </>
            )}
            {result && (
                <div role="status">
                    <p>운영 요청 결과</p>
                    <pre className="json">{JSON.stringify(result, null, 2)}</pre>
                </div>
            )}
        </section>
    );
}

export default function AdminMembershipPayments() {
    const [filter, setFilter] = useState({ status: '', membershipId: '', opsReviewOnly: false, blockedOnly: false });
    const [page, setPage] = useState(0);
    const [selected, setSelected] = useState(null);
    const { data, error, reload } = useLoad(
        () =>
            api.get('/api/admin/membership-payment-attempts', {
                ...filter,
                status: filter.status || undefined,
                page,
                size: 20,
            }),
        [filter, page],
    );
    const change = (key, value) => {
        setPage(0);
        setFilter((prev) => ({ ...prev, [key]: value }));
    };
    return (
        <>
            <div className="admin__head">
                <h1 className="page__title">멤버십 결제·환불</h1>
            </div>
            <div className="filters">
                <select
                    className="field__input"
                    aria-label="결제 시도 상태"
                    value={filter.status}
                    onChange={(e) => change('status', e.target.value)}
                >
                    <option value="">전체 상태</option>
                    {Object.entries(STATUSES).map(([key, label]) => (
                        <option key={key} value={key}>
                            {label}
                        </option>
                    ))}
                </select>
                <input
                    className="field__input"
                    type="number"
                    min="1"
                    placeholder="이용권 ID"
                    value={filter.membershipId}
                    onChange={(e) => change('membershipId', e.target.value)}
                />
                <label>
                    <input
                        type="checkbox"
                        checked={filter.opsReviewOnly}
                        onChange={(e) => change('opsReviewOnly', e.target.checked)}
                    />{' '}
                    운영 확인만
                </label>
                <label>
                    <input
                        type="checkbox"
                        checked={filter.blockedOnly}
                        onChange={(e) => change('blockedOnly', e.target.checked)}
                    />{' '}
                    정산 차단만
                </label>
            </div>
            <ErrorNotice error={error} />
            {selected && <AttemptDetail key={selected} attemptId={selected} onChanged={reload} />}
            <div className="card table-wrap">
                <table className="table">
                    <thead>
                        <tr>
                            <th>시도 ID</th>
                            <th>이용권</th>
                            <th>회차</th>
                            <th>상태</th>
                            <th>접수</th>
                            <th>운영 확인</th>
                        </tr>
                    </thead>
                    <tbody>
                        {data?.content.map((item) => (
                            <tr key={item.attemptId}>
                                <td>
                                    <button className="chip" onClick={() => setSelected(item.attemptId)}>
                                        {item.attemptId} 상세
                                    </button>
                                </td>
                                <td>{item.membershipId}</td>
                                <td>{item.membershipPaymentId}</td>
                                <td>{STATUSES[item.status] ?? item.status}</td>
                                <td>{formatDateTime(item.requestedAt)}</td>
                                <td>{item.opsReviewReason ?? '-'}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {data?.content.length === 0 && <p className="state-message">결제 시도가 없습니다.</p>}
            </div>
            {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
        </>
    );
}
