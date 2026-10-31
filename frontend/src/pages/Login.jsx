import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';
import { clearPendingAddress, readPendingAddress, registerAddress } from '../pendingAddress';

export default function Login() {
  const { member, login, reloadMember } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  // 시작 화면에서 확인해 둔 주소를 배송지로 저장하는 중(saving)인지, 실패했는지(failed)
  const [saveState, setSaveState] = useState('idle');
  // 개발 모드에서 효과가 두 번 돌아도 같은 배송지가 두 번 저장되지 않게 한다
  const saveStarted = useRef(false);
  const destination = location.state?.from ?? '/recipes';

  useEffect(() => {
    if (!member) return;
    if (member.currentAddress) {
      // 이미 배송지가 있으면 보관한 주소는 쓰지 않는다
      clearPendingAddress();
      return;
    }
    const pending = readPendingAddress();
    if (!pending || saveStarted.current) return;
    saveStarted.current = true;
    setSaveState('saving');
    let saved = false;
    registerAddress(pending)
      .then(() => {
        saved = true;
        clearPendingAddress();
        return reloadMember();
      })
      .catch((e) => {
        // 저장은 됐고 회원 정보만 못 읽은 경우: 다시 저장하면 같은 배송지가 또 생기므로 새로고침으로 복구한다
        if (saved) {
          window.location.reload();
          return;
        }
        setError(e);
        setSaveState('failed');
      });
  }, [member, reloadMember]);

  if (member) {
    // 배송지가 생기면 원래 가려던 곳으로 간다
    if (member.currentAddress) return <Navigate to={destination} replace />;
    if (saveState === 'saving' || (saveState === 'idle' && readPendingAddress())) {
      return <p className="state-message">배송지를 저장하는 중입니다.</p>;
    }
    // 배송지가 없으면 동네부터 정하게 한다(실패했다면 보관한 주소를 채워 다시 시도할 수 있다)
    return <Navigate to="/start" replace state={{ next: destination, notice: error?.message }} />;
  }

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      // 로그인되면 위의 Navigate가 알맞은 곳으로 보낸다
      await login(email, password);
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container page">
      <form className="card auth-card form" onSubmit={handleSubmit}>
        <h1 className="page__title">로그인</h1>
        <ErrorNotice error={error} />
        <label className="field">
          <span className="field__label">이메일</span>
          <input
            className="field__input"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label className="field">
          <span className="field__label">비밀번호</span>
          <input
            className="field__input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit" className="btn btn--large btn--block" disabled={submitting}>
          {submitting ? '로그인 중...' : '로그인'}
        </button>
        <p className="auth-card__footer">
          아직 회원이 아니신가요? <Link to="/signup">회원가입</Link>
        </p>
        <p className="auth-card__footer">
          <Link to="/admin/login">관리자 로그인</Link>
        </p>
      </form>
    </div>
  );
}
