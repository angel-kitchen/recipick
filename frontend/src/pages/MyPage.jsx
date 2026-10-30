import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import { MEMBERSHIP_TYPE, formatDateTime } from '../labels';

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

function WithdrawForm({ onDone }) {
  const [password, setPassword] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const withdraw = async () => {
    setConfirming(false);
    setError(null);
    setSubmitting(true);
    try {
      // 포인트 기능이 아직 없어 잔액은 0으로 확인한다
      await api.post('/api/members/me/withdraw', { password, acknowledgedPointBalance: 0 });
      onDone();
    } catch (e) {
      setError(e);
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
      <label className="field">
        <span className="field__label">비밀번호 확인</span>
        <input className="field__input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} maxLength={20} required />
      </label>
      <div className="row-actions">
        <button type="submit" className="btn btn--outline" disabled={submitting}>
          회원 탈퇴
        </button>
      </div>
      {confirming && (
        <ConfirmModal title="정말 탈퇴할까요?" confirmLabel="탈퇴" cancelLabel="취소" onConfirm={withdraw} onCancel={() => setConfirming(false)}>
          탈퇴하면 이 계정으로 더 이상 로그인할 수 없어요.
        </ConfirmModal>
      )}
    </form>
  );
}

export default function MyPage() {
  const { member, logout } = useAuth();
  const navigate = useNavigate();

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
            <dd>{member.mart?.name ?? '배송지를 등록하면 정해져요'}</dd>
            <dt>현재 배송지</dt>
            <dd className="info-list__row">
              <span>
                {member.currentAddress ? (
                  <>
                    {member.currentAddress.address} {member.currentAddress.detailAddress}{' '}
                    {!member.currentAddress.serviceAvailable && <span className="badge badge--warn">서비스 지역 아님</span>}
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
              <Link to="/membership" className="btn btn--muted">
                자세히
              </Link>
            </dd>
          </dl>
        </section>

        <section className="card">
          <h2 className="tile__title">비밀번호 변경</h2>
          <PasswordForm onDone={leave} />
        </section>

        <section className="card">
          <h2 className="tile__title">회원 탈퇴</h2>
          <p className="field__hint">진행 중인 주문이나 결제가 있으면 탈퇴할 수 없어요.</p>
          <WithdrawForm onDone={leave} />
        </section>
      </div>
    </div>
  );
}
