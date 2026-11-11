import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import useRecipeSuggestions from '../hooks/useRecipeSuggestions';
import Icon from './Icon';
import Logo from './Logo';
import ErrorNotice from './ErrorNotice';

export default function Header() {
  const { member, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [cartCount, setCartCount] = useState(0);
  const [logoutError, setLogoutError] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const menuRef = useRef(null);
  const { inputProps: suggestionProps, list: suggestionList } = useRecipeSuggestions(keyword, 'header-recipe-suggestions');

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
    // 담기가 연달아 일어나면 조회가 겹친다. 늦게 도착한 옛 응답이 최신 개수를 덮지 않게 마지막 조회만 반영한다
    let latest = 0;
    const reload = () => {
      const mine = ++latest;
      return api
        .get('/api/cart-items')
        .then((cart) => !ignore && mine === latest && setCartCount(cart.items.length))
        .catch(() => {});
    };
    reload();
    window.addEventListener('cart-changed', reload);
    return () => {
      ignore = true;
      window.removeEventListener('cart-changed', reload);
    };
  }, [member, pathname]);

  // 레시피 목록의 검색어(keyword)로 넘긴다. 레시피 API는 회원만 쓸 수 있어 로그인했을 때만 검색창을 둔다
  const handleSearch = (event) => {
    event.preventDefault();
    navigate(`/recipes?keyword=${encodeURIComponent(keyword.trim())}`);
  };

  const handleLogout = async () => {
    setLogoutError(null);
    setLoggingOut(true);
    try {
      await logout();
      navigate('/');
    } catch (e) {
      setLogoutError(new Error('서버의 로그인 세션을 종료하지 못했어요. 다시 시도해 주세요.'));
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <header className="header header--shop">
      {logoutError && <div className="container"><ErrorNotice error={logoutError} /></div>}
      <div className="header__inner container">
        <Link to="/" className="header__logo" aria-label="Recipick 홈">
          <Logo />
        </Link>
        {member?.currentAddress && (
          <Link to="/addresses" className="header__address" title="배송지 변경">
            {member.currentAddress.address}
          </Link>
        )}
        {member && (
          <form className="header__search suggest" role="search" onSubmit={handleSearch}>
            <input
              type="search"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              placeholder="오늘 저녁, 무엇을 만들까요?"
              aria-label="레시피 검색"
              {...suggestionProps}
            />
            <button type="submit" aria-label="검색">
              <Icon name="search" size={18} />
            </button>
            {suggestionList}
          </form>
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
              <NavLink to="/membership-info" className="header__item">
                <Icon name="membership" />
                <span>멤버십</span>
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
                <summary className="header__item" title={`${member.name}님`}>
                  <Icon name="user" />
                  <span>
                    <span className="header__member-name">{member.name}</span>님 ▾
                  </span>
                </summary>
                <div className="header__menu-list">
                  <NavLink to="/mypage">내 정보</NavLink>
                  <NavLink to="/orders">주문 내역</NavLink>
                  <NavLink to="/bookmarks">찜한 레시피</NavLink>
                  <NavLink to="/membership">내 멤버십</NavLink>
                  <NavLink to="/addresses">배송지 관리</NavLink>
                  <button type="button" className="header__link-button" onClick={handleLogout} disabled={loggingOut}>
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
