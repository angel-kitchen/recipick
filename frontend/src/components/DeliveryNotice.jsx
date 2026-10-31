import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { deliveryBlock } from '../delivery';

// 배송지가 없거나 서비스 지역이 아니면 모든 화면 위에 이유와 다음에 할 일을 계속 보여 준다
export default function DeliveryNotice() {
  const { member } = useAuth();
  const { pathname } = useLocation();
  const block = member ? deliveryBlock(member) : null;
  // 홈은 영상이 화면을 채우고, 배송지·시작 화면은 같은 안내를 이미 보여 준다
  if (!block || pathname === '/' || pathname.startsWith('/addresses') || pathname.startsWith('/start')) return null;

  return (
    <div className="container delivery-notice">
      <div className={`notice notice--action ${block.tone === 'info' ? 'notice--info' : ''}`} role="status">
        <span>{block.notice}</span>
        <Link to="/addresses" className="btn btn--muted">
          {block.label}
        </Link>
      </div>
    </div>
  );
}
