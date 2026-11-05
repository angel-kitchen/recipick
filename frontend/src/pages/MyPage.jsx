import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import { MEMBERSHIP_TYPE, formatDateTime, formatPoint } from '../labels';

function PasswordForm({ onDone }) {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const change = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.patch('/api/members/me/password', form);
      onDone();
    } catch (e) {
      setError(e);
      setSubmitting(false);
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <ErrorNotice error={error} />
      <label className="field">
        <span className="field__label">현재 비밀번호</span>
        <input className="field__input" type="password" autoComplete="current-password" value={form.currentPassword} onChange={change('currentPassword')} maxLength={20} required />
      </label>
      <label className="field">
        <span className="field__label">새 비밀번호</span>
        <input className="field__input" type="password" autoComplete="new-password" value={form.newPassword} onChange={change('newPassword')} minLength={8} maxLength={20} required />
        <span className="field__hint">8~20자, 영문·숫자·특수문자를 모두 포함해 주세요.</span>
      </label>
      <p className="field__hint">비밀번호를 바꾸면 모든 기기에서 로그아웃되어 다시 로그인해야 해요.</p>
      <div className="row-actions">
        <button type="submit" className="btn" disabled={submitting}>
          {submitting ? '변경 중...' : '비밀번호 변경'}
        </button>
      </div>
    </form>
  );
}

// 탈퇴는 안내한 잔액과 실제 잔액이 같아야 진행된다. 잔액을 모르면 탈퇴 버튼을 막는다
function WithdrawForm({ pointBalance, onPointChanged, onDone }) {
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const withdraw = async () => {
    setConfirming(false);
    setError(null);
    setSubmitting(true);
    try {
      await api.post('/api/members/me/withdraw', { password, acknowledgedPointBalance: pointBalance });
      onDone();
    } catch (e) {
      // 안내 뒤 잔액이 바뀌었다. 최신 잔액을 다시 보여 주고 다시 확인받는다
      if (e.code === 'POINT-002') onPointChanged();
      const guidance = {
        'BILLING-030': '저장된 카드를 정리하고 있어요. 잠시 후 다시 시도해 주세요. 카드 정리는 탈퇴가 취소되어도 되돌릴 수 없어요.',
        'BILLING-031': '카드 정리에 운영자 확인이 필요해요. 문의 후 다시 시도해 주세요.',
        'MEMBER-008': '탈퇴 정산 연결을 준비 중이라 현재 탈퇴할 수 없어요. 잠시 후 다시 확인해 주세요.',
      };
      setError(guidance[e.code] ? new Error(guidance[e.code]) : e);
      setSubmitting(false);
    }
  };

  return (
    <form
      className="form"
      onSubmit={(event) => {
        event.preventDefault();
        setConfirming(true);
      }}
    >
      <ErrorNotice error={error} />
      <p className="field__hint">탈퇴 준비 중 저장된 카드가 먼저 삭제될 수 있으며, 탈퇴가 완료되지 않아도 카드 삭제는 되돌릴 수 없어요.</p>
      <p className="field__hint">멤버십 이용 이력이 있으면 무료체험 반복 이용 방지를 위해 휴대전화의 키 해시(HMAC-SHA-256)를 탈퇴 시점부터 1년간 보관한 뒤 삭제해요. 해당 기간에도 재가입과 유료 구독은 가능해요.</p>
      <label className="field">
        <span className="field__label">비밀번호 확인</span>
        <input className="field__input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} maxLength={20} required />
      </label>
      <div className="row-actions">
        <button type="submit" className="btn btn--outline" disabled={submitting || pointBalance === null}>
          회원 탈퇴
        </button>
      </div>
      {confirming && (
        <ConfirmModal title="정말 탈퇴할까요?" confirmLabel="탈퇴" cancelLabel="취소" onConfirm={withdraw} onCancel={() => setConfirming(false)}>
          탈퇴하면 이 계정으로 더 이상 로그인할 수 없어요. 저장된 카드 정리는 탈퇴 완료 전에도 진행될 수 있고 되돌릴 수 없어요.
          {pointBalance > 0 && ` 남은 포인트 ${formatPoint(pointBalance)}는 모두 소멸돼요.`}
        </ConfirmModal>
      )}
    </form>
  );
}

export default function MyPage() {
  const { member, logout } = useAuth();
  const navigate = useNavigate();
  const [point, setPoint] = useState(null);
  const [pointError, setPointError] = useState(null);

  const loadPoint = useCallback(async () => {
    setPointError(null);
    try {
      setPoint(await api.get('/api/points'));
    } catch (e) {
      setPoint(null);
      setPointError(e);
    }
  }, []);

  useEffect(() => {
    loadPoint();
  }, [loadPoint]);

  // 서버가 비밀번호 변경·탈퇴 때 리프레시 쿠키를 지우므로 화면의 로그인 상태도 함께 정리한다
  const leave = async () => {
    try {
      await logout();
    } catch {
      // 이미 세션이 끝난 상태라 로그아웃 요청이 실패해도 화면은 로그아웃으로 둔다
    }
    navigate('/login');
  };

  return (
    <div className="container page">
      <h1 className="page__title">내 정보</h1>
      <div className="row-actions"><Link to="/payments" className="btn btn--outline">결제 내역</Link><Link to="/points/earnings" className="btn btn--outline">포인트 적립 내역</Link><Link to="/points/history" className="btn btn--outline">포인트 전체 이력</Link></div>
      <div className="stack">
        <section className="card">
          <h2 className="tile__title">기본 정보</h2>
          <dl className="info-list">
            <dt>이메일</dt>
            <dd>{member.email}</dd>
            <dt>이름</dt>
            <dd>{member.name}</dd>
            <dt>휴대전화</dt>
            <dd>{member.phone}</dd>
            <dt>이용 마트</dt>
            <dd>{member.mart?.name ?? (member.currentAddress ? '지정된 이용 마트가 없어요' : '배송지를 등록하면 정해져요')}</dd>
            <dt>현재 배송지</dt>
            <dd className="info-list__row">
              <span>
                {member.currentAddress ? (
                  <>
                    {member.currentAddress.address} {member.currentAddress.detailAddress}{' '}
                    {!member.currentAddress.serviceAvailable && <span className="badge badge--warn">{{ MART_CLOSED: '마트 운영 중지', MART_MISMATCH: '지정 마트 불일치', OUTSIDE_SERVICE_AREA: '서비스 지역 아님' }[member.currentAddress.unavailableReason] ?? '현재 이용 불가'}</span>}
                  </>
                ) : (
                  '등록된 배송지가 없어요'
                )}
              </span>
              <Link to="/addresses" className="btn btn--muted">
                관리
              </Link>
            </dd>
            <dt>멤버십</dt>
            <dd className="info-list__row">
              <span>
                {member.membership?.active
                  ? `${MEMBERSHIP_TYPE[member.membership.type] ?? member.membership.type} · ${formatDateTime(member.membership.endAt)}까지`
                  : '이용 중이 아니에요'}
              </span>
              <Link to={member.membership?.active ? '/membership' : '/membership-info'} className="btn btn--muted">
                자세히
              </Link>
            </dd>
          </dl>
        </section>

        <section className="card">
          <h2 className="tile__title">포인트</h2>
          <ErrorNotice error={pointError} />
          {point && (
            <dl className="info-list">
              <dt>사용 가능</dt>
              <dd>{formatPoint(point.availableBalance)}</dd>
              <dt>소멸 예정</dt>
              <dd>
                {point.nearestExpiringAmount > 0
                  ? `${formatPoint(point.nearestExpiringAmount)} · ${point.nearestExpiresOn}까지 사용 가능`
                  : '없어요'}
              </dd>
            </dl>
          )}
        </section>

        <section className="card">
          <h2 className="tile__title">비밀번호 변경</h2>
          <PasswordForm onDone={leave} />
        </section>

        <section className="card">
          <h2 className="tile__title">회원 탈퇴</h2>
          <p className="field__hint">진행 중인 주문이나 결제가 있으면 탈퇴할 수 없어요.</p>
          <WithdrawForm pointBalance={point?.availableBalance ?? null} onPointChanged={loadPoint} onDone={leave} />
        </section>
      </div>
    </div>
  );
}
