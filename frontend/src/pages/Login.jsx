import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';

export default function Login() {
  const { member, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const destination = location.state?.from ?? '/recipes';

  if (member) return <Navigate to={destination} replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(destination, { replace: true });
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
