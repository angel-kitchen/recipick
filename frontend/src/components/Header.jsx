import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Header() {
  const { member, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const isHome = pathname === '/';

  // 홈에서는 영상 위에 떠 있다가 스크롤하면 흰 바탕이 된다(테슬라 방식)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => setOpen(false), [pathname]);

  const overHero = isHome && !scrolled && !open;

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  return (
    <header className={`header ${overHero ? 'header--over-hero' : ''} ${isHome ? '' : 'header--shop'}`}>
      <div className="header__inner container">
        <Link to="/" className="header__logo">
          Recipick
        </Link>
        {member?.currentAddress && (
          <Link to="/addresses" className="header__address" title="배송지 변경">
            {member.currentAddress.address}
          </Link>
        )}
        <button
          type="button"
          className="header__toggle"
          aria-label="메뉴 열기"
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          <span />
          <span />
          <span />
        </button>
        <nav className={`header__nav ${open ? 'header__nav--open' : ''}`}>
          {member && (
            <>
              <NavLink to="/recipes">레시피</NavLink>
              <NavLink to="/products">상품</NavLink>
              <NavLink to="/cart">장바구니</NavLink>
              <NavLink to="/addresses">배송지</NavLink>
            </>
          )}
          <span className="header__spacer" />
          {member ? (
            <>
              <span className="header__name">{member.name}님</span>
              <button type="button" className="header__link-button" onClick={handleLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <NavLink to="/login">로그인</NavLink>
              <NavLink to="/signup">회원가입</NavLink>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
