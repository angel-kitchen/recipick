import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

// 상품·장바구니·주문은 이용 마트가 있어야 쓸 수 있다. ProtectedRoute 안에서만 쓰므로 member는 항상 있다
export default function RequireDelivery() {
  const { member } = useAuth();
  const location = useLocation();

  // 배송지가 없으면 동네부터 정하게 하고, 끝나면 원래 가려던 곳으로 보낸다
  if (!member.currentAddress) {
    return <Navigate to="/start" replace state={{ next: location.pathname + location.search }} />;
  }
  // 서비스 지역 밖이면 보낼 곳이 없어 이유만 알린다. 배송지를 바꾸는 버튼은 화면 위 안내 띠(DeliveryNotice)에 있다
  if (!member.mart) {
    return (
      <div className="container page">
        <p className="state-message">서비스 지역의 배송지를 선택하면 이용할 수 있어요.</p>
      </div>
    );
  }
  return <Outlet />;
}
