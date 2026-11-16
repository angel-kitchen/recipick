import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import ErrorNotice from '../../components/ErrorNotice';
import { useAdmin } from '../AdminContext';

export default function AdminLogin() {
  const { admin, loading, login } = useAdmin();
  const navigate = useNavigate();
  const [form, setForm] = useState({ loginId: '', password: '' });
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (loading) return null;
  if (admin) return <Navigate to="/admin" replace />;

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(form.loginId, form.password);
      navigate('/admin', { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="admin-login">
      <form className="card form auth-card" onSubmit={handleSubmit}>
        <h1 className="page__title">관리자 로그인</h1>
        <ErrorNotice error={error} />
        <label className="field">
          <span className="field__label">관리자 아이디</span>
          <input
            className="field__input"
            value={form.loginId}
            maxLength={50}
            autoComplete="username"
            onChange={(e) => setForm((prev) => ({ ...prev, loginId: e.target.value }))}
            required
          />
        </label>
        <label className="field">
          <span className="field__label">비밀번호</span>
          <input
            className="field__input"
            type="password"
            value={form.password}
            autoComplete="current-password"
            onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))}
            required
          />
        </label>
        <button type="submit" className="btn btn--large btn--block" disabled={submitting}>
          {submitting ? '로그인 중...' : '로그인'}
        </button>
        <p className="field__hint" style={{ textAlign: 'center' }}>
          <Link to="/login">회원 로그인으로 이동</Link>
        </p>
      </form>
    </div>
  );
}
