import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Footer from './Footer';
import DeliveryNotice from './DeliveryNotice';
import Header from './Header';
import MembershipInfoModal from './MembershipInfoModal';
import { ToastProvider } from './Toast';

// 첫 동네 설정·가입 화면·결제 흐름에서는 체험 안내로 끊지 않는다
const TRIAL_OFFER_EXCLUDED = ['/start', '/membership', '/checkout'];
const HIDE_TODAY_KEY = 'trialOfferHiddenDate';
const todayInSeoul = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });

export default function Layout() {
  const { member } = useAuth();
  const { pathname, search } = useLocation();
  const showCartButton = member && pathname !== '/cart' && !pathname.startsWith('/checkout');
  const [trialOffer, setTrialOffer] = useState(false);
  // 닫으면 사이트에 새로 들어오거나 새로고침할 때까지 다시 띄우지 않는다. 다른 회원으로 로그인하면 다시 띄운다
  const [closedFor, setClosedFor] = useState(null);

  // 체험할 수 있는 비회원에게 띄운다. 모바일 결제·카드 등록에서 돌아온 화면은 결과 처리가 먼저다
  useEffect(() => {
    const offerable =
      member &&
      !member.membership?.active &&
      member.trialEligible &&
      !TRIAL_OFFER_EXCLUDED.includes(pathname) &&
      !new URLSearchParams(search).has('paymentId') &&
      closedFor !== member.memberId &&
      localStorage.getItem(HIDE_TODAY_KEY) !== todayInSeoul();
    setTrialOffer(Boolean(offerable));
  }, [member, pathname, search, closedFor]);

  const closeTrialOffer = () => {
    setClosedFor(member.memberId);
    setTrialOffer(false);
  };

  const hideTrialOfferToday = () => {
    localStorage.setItem(HIDE_TODAY_KEY, todayInSeoul());
    closeTrialOffer();
  };

  return (
    <ToastProvider>
      <Header />
      <main className="main">
        <DeliveryNotice />
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
      {trialOffer && <MembershipInfoModal onClose={closeTrialOffer} onHideToday={hideTrialOfferToday} />}
    </ToastProvider>
  );
}
