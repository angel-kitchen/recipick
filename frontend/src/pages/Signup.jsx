import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import ErrorNotice from '../components/ErrorNotice';

const INITIAL = { email: '', name: '', password: '', phone: '', termsAgreed: false, privacyAgreed: false };

export default function Signup() {
  const navigate = useNavigate();
  const [form, setForm] = useState(INITIAL);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const change = (key) => (event) =>
    setForm((prev) => ({
      ...prev,
      [key]: event.target.type === 'checkbox' ? event.target.checked : event.target.value,
    }));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.post('/api/auth/signup', form);
      navigate('/login', { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container page">
      <form className="card auth-card form" onSubmit={handleSubmit}>
        <h1 className="page__title">회원가입</h1>
        <ErrorNotice error={error} />
        <label className="field">
          <span className="field__label">이메일</span>
          <input className="field__input" type="email" value={form.email} onChange={change('email')} required />
        </label>
        <label className="field">
          <span className="field__label">이름</span>
          <input className="field__input" value={form.name} onChange={change('name')} maxLength={50} required />
        </label>
        <label className="field">
          <span className="field__label">비밀번호</span>
          <input
            className="field__input"
            type="password"
            autoComplete="new-password"
            value={form.password}
            onChange={change('password')}
            minLength={8}
            maxLength={20}
            required
          />
          <span className="field__hint">8~20자, 영문·숫자·특수문자를 모두 포함해 주세요.</span>
        </label>
        <label className="field">
          <span className="field__label">휴대전화</span>
          <input
            className="field__input"
            inputMode="numeric"
            placeholder="01012345678"
            value={form.phone}
            onChange={change('phone')}
            required
          />
          <span className="field__hint">숫자만 입력해 주세요.</span>
        </label>
        <label className="check">
          <input type="checkbox" checked={form.termsAgreed} onChange={change('termsAgreed')} />
          이용약관에 동의합니다.
        </label>
        <label className="check">
          <input type="checkbox" checked={form.privacyAgreed} onChange={change('privacyAgreed')} />
          개인정보 수집·이용에 동의합니다.
        </label>
        <button type="submit" className="btn btn--large btn--block" disabled={submitting}>
          {submitting ? '가입 중...' : '가입하기'}
        </button>
        <p className="auth-card__footer">
          이미 회원이신가요? <Link to="/login">로그인</Link>
        </p>
        <p className="auth-card__footer">
          <Link to="/admin/login">관리자 로그인</Link>
        </p>
      </form>
    </div>
  );
}
