import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Footer from './Footer';
import Header from './Header';
import { ToastProvider } from './Toast';

export default function Layout() {
  const { member } = useAuth();
  const { pathname } = useLocation();
  const showCartButton = member && pathname !== '/cart' && !pathname.startsWith('/checkout');

  return (
    <ToastProvider>
      <Header />
      <main className="main">
        <Outlet />
      </main>
      <Footer />
      {showCartButton && (
        <Link to="/cart" className="frap" aria-label="장바구니로 이동">
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M6 7h12l-1 13H7L6 7z" strokeLinejoin="round" />
            <path d="M9 7a3 3 0 0 1 6 0" strokeLinecap="round" />
          </svg>
        </Link>
      )}
    </ToastProvider>
  );
}
