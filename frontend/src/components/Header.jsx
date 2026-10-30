import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import Icon from './Icon';

export default function Header() {
  const { member, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [cartCount, setCartCount] = useState(0);
  const menuRef = useRef(null);
  const isHome = pathname === '/';

  // 홈에서는 영상 위에 떠 있다가 스크롤하면 흰 바탕이 된다(테슬라 방식)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 40);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    setOpen(false);
    if (menuRef.current) menuRef.current.open = false;
  }, [pathname]);

  // 쿠팡·네이버처럼 장바구니 아이콘 옆에 담긴 개수를 보여 준다. 화면을 옮길 때와 담기·삭제 뒤에 새로 읽는다
  useEffect(() => {
    if (!member) {
      setCartCount(0);
      return undefined;
    }
    let ignore = false;
    const reload = () =>
      api
        .get('/api/cart-items')
        .then((cart) => !ignore && setCartCount(cart.items.length))
        .catch(() => {});
    reload();
    window.addEventListener('cart-changed', reload);
    return () => {
      ignore = true;
      window.removeEventListener('cart-changed', reload);
    };
  }, [member, pathname]);

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
              <NavLink to="/recipes" className="header__item">
                <Icon name="recipe" />
                <span>레시피</span>
              </NavLink>
              <NavLink to="/products" className="header__item">
                <Icon name="product" />
                <span>상품</span>
              </NavLink>
            </>
          )}
          <span className="header__spacer" />
          {member ? (
            <>
              <NavLink to="/bookmarks" className="header__item">
                <Icon name="heart" />
                <span>찜</span>
              </NavLink>
              <NavLink to="/cart" className="header__item">
                <Icon name="cart" />
                <span>장바구니</span>
                {cartCount > 0 && <span className="header__count">{cartCount}</span>}
              </NavLink>
              <details className="header__menu" ref={menuRef}>
                <summary className="header__item">
                  <Icon name="user" />
                  <span>{member.name}님 ▾</span>
                </summary>
                <div className="header__menu-list">
                  <NavLink to="/mypage">내 정보</NavLink>
                  <NavLink to="/orders">주문 내역</NavLink>
                  <NavLink to="/bookmarks">찜한 레시피</NavLink>
                  <NavLink to="/auto-carts">자동 담기 기록</NavLink>
                  <NavLink to="/membership">내 멤버십</NavLink>
                  <NavLink to="/addresses">배송지 관리</NavLink>
                  <button type="button" className="header__link-button" onClick={handleLogout}>
                    로그아웃
                  </button>
                </div>
              </details>
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
