import { useEffect, useState } from 'react';
import { api } from '../api/client';
import ErrorNotice from '../components/ErrorNotice';
import { MEMBERSHIP_SOURCE, MEMBERSHIP_TYPE, formatDateTime } from '../labels';

// 결제·구독 연결 전이라 조회만 한다
export default function Membership() {
  const [membership, setMembership] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/api/memberships/me').then(setMembership).catch(setError);
  }, []);

  if (!membership) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <p className="state-message">불러오는 중입니다.</p>}
      </div>
    );
  }

  const { current } = membership;
  return (
    <div className="container page">
      <h1 className="page__title">내 멤버십</h1>
      <section className="card">
        <h2 className="tile__title">
          {membership.active ? '멤버십 이용 중' : '멤버십 회원이 아닙니다'}{' '}
          <span className={`badge ${membership.active ? '' : 'badge--warn'}`}>{membership.active ? '이용 중' : '미가입'}</span>
        </h2>
        {current ? (
          <dl className="info-list">
            <dt>종류</dt>
            <dd>{MEMBERSHIP_TYPE[current.type] ?? current.type}</dd>
            <dt>가입 경로</dt>
            <dd>{MEMBERSHIP_SOURCE[current.source] ?? current.source}</dd>
            <dt>시작</dt>
            <dd>{formatDateTime(current.startAt)}</dd>
            <dt>종료</dt>
            <dd>{formatDateTime(current.endAt)}</dd>
            <dt>자동 갱신</dt>
            <dd>{current.autoRenew ? '사용' : '사용 안 함'}</dd>
            {current.cancelReservedAt && (
              <>
                <dt>해지 예약</dt>
                <dd>{formatDateTime(current.cancelReservedAt)}</dd>
              </>
            )}
          </dl>
        ) : (
          <p className="field__hint">자동 장바구니는 멤버십 회원만 이용할 수 있어요.</p>
        )}
        {membership.trialEligible && !membership.active && (
          <p className="field__hint" style={{ marginTop: 12 }}>무료 체험을 이용할 수 있는 회원입니다. 체험 신청은 아직 연결되지 않았습니다.</p>
        )}
      </section>
    </div>
  );
}
