import { useEffect, useState } from 'react';
import { Outlet, useLocation, useNavigationType } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Footer from './Footer';
import FloatingRecent from './FloatingRecent';
import DeliveryNotice from './DeliveryNotice';
import Header from './Header';
import MembershipInfoModal from './MembershipInfoModal';
import { ToastProvider } from './Toast';

// 첫 동네 설정·가입 화면·결제 흐름에서는 체험 안내로 끊지 않는다
const TRIAL_OFFER_EXCLUDED = ['/start', '/membership', '/checkout'];
const HIDE_TODAY_KEY = 'trialOfferHiddenDate';
const todayInSeoul = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });

function hiddenToday() {
  try {
    return localStorage.getItem(HIDE_TODAY_KEY) === todayInSeoul();
  } catch {
    // 저장소를 못 쓰면 숨김 기록이 없는 것으로 보고 이번 방문에 안내한다
    return false;
  }
}

export default function Layout() {
  const { member } = useAuth();
  const { pathname, search } = useLocation();
  const navigationType = useNavigationType();
  const [trialOffer, setTrialOffer] = useState(false);
  // 닫으면 사이트에 새로 들어오거나 새로고침할 때까지 다시 띄우지 않는다. 다른 회원으로 로그인하면 다시 띄운다
  const [closedFor, setClosedFor] = useState(null);

  // SPA는 화면을 바꿔도 앞 화면의 스크롤 위치가 남아 새 화면이 중간부터 보인다.
  // 뒤로 가기는 보던 위치로 돌아가는 게 자연스러워 새로 이동할 때만 맨 위로 올린다
  useEffect(() => {
    if (navigationType !== 'POP') {
      window.scrollTo(0, 0);
    }
  }, [pathname, navigationType]);

  // 체험할 수 있는 비회원에게 띄운다. 모바일 결제·카드 등록에서 돌아온 화면은 결과 처리가 먼저다
  useEffect(() => {
    const offerable =
      member &&
      !member.membership?.active &&
      member.trialEligible &&
      !TRIAL_OFFER_EXCLUDED.includes(pathname) &&
      !new URLSearchParams(search).has('paymentId') &&
      closedFor !== member.memberId &&
      !hiddenToday();
    setTrialOffer(Boolean(offerable));
  }, [member, pathname, search, closedFor]);

  const closeTrialOffer = () => {
    setClosedFor(member.memberId);
    setTrialOffer(false);
  };

  const hideTrialOfferToday = () => {
    try {
      localStorage.setItem(HIDE_TODAY_KEY, todayInSeoul());
    } catch {
      // 저장하지 못해도 지금 창은 닫는다. 새로고침하면 다시 보일 뿐이다
    }
    closeTrialOffer();
  };

  return (
    <ToastProvider>
      <Header />
      <main className="main">
        <DeliveryNotice />
        <Outlet />
      </main>
      <FloatingRecent />
      <Footer />
      {trialOffer && <MembershipInfoModal onClose={closeTrialOffer} onHideToday={hideTrialOfferToday} />}
    </ToastProvider>
  );
}
