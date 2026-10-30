import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';

export default function Login() {
  const { member, login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const destination = location.state?.from ?? '/recipes';

  // 배송지가 없으면 서비스 이용의 첫 단계로 배송지 등록을 먼저 안내한다(건너뛰면 원래 가려던 곳으로 간다)
  if (member) {
    return member.currentAddress ? (
      <Navigate to={destination} replace />
    ) : (
      <Navigate to="/addresses" replace state={{ welcome: true, next: destination }} />
    );
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
