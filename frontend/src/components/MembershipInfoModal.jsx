import { useEffect } from 'react';
import { Link } from 'react-router-dom';

// 멤버십이 무엇인지 처음 보는 사람에게 설명하는 안내 창. 가입·결제는 여기서 하지 않고 내 멤버십 화면으로 안내한다
export default function MembershipInfoModal({ onClose }) {
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label="멤버십 안내">
      <div className="modal__box card">
        <button type="button" className="modal__close" aria-label="닫기" onClick={onClose}>
          ×
        </button>
        <h2 className="tile__title">멤버십이 뭐예요?</h2>
        <p className="field__hint" style={{ margin: '8px 0 12px' }}>
          멤버십 회원이 되면 장보기가 훨씬 편해져요.
        </p>
        <ul className="benefit-list">
          <li>
            <strong>자동 장바구니</strong>
            <span>레시피를 고르면 필요한 재료가 우리 동네 마트 상품으로 알아서 담겨요.</span>
          </li>
          <li>
            <strong>AI 검색</strong>
            <span>&quot;김치찌개 2인분&quot;처럼 말로 입력하거나 &quot;매운 음식 30분 이내&quot;처럼 조건만 적어도 레시피를 찾아 줘요.</span>
          </li>
        </ul>
        <dl className="info-list">
          <dt>요금</dt>
          <dd>월 3,900원</dd>
          <dt>무료 체험</dt>
          <dd>처음 한 번, 7일 동안 써 볼 수 있어요. 체험을 시작할 때는 결제되지 않아요.</dd>
        </dl>
        <div className="row-actions">
          <Link to="/membership" className="btn btn--large" onClick={onClose}>
            내 멤버십 보기
          </Link>
          <button type="button" className="btn btn--large btn--outline" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
