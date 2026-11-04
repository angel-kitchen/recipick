import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import Pictogram from './Pictogram';

// 멤버십이 무엇인지 처음 보는 사람에게 설명하는 안내 창. 가입·결제는 여기서 하지 않고 멤버십 소개 페이지로 안내한다
// onHideToday를 넘긴 곳(로그인 직후 안내)에서만 "오늘 하루 안 보기"를 보여 주고, 그때 버튼 글은 무료 체험으로 바뀐다
export default function MembershipInfoModal({ onClose, onHideToday }) {
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="modal" role="dialog" aria-modal="true" aria-label="멤버십 안내">
      <div className="modal__box card promo">
        <button type="button" className="modal__close" aria-label="닫기" onClick={onClose}>
          ×
        </button>
        <div className="promo__art" aria-hidden="true">
          <Pictogram name="kimchi" size={56} />
          <Pictogram name="bowl" size={76} />
          <Pictogram name="tofu" size={56} />
          <span className="promo__ribbon">7일 무료</span>
        </div>
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
          <dd>
            처음 한 번, 7일 동안 써 볼 수 있어요. 체험을 시작할 때는 결제되지 않아요. 체험이 끝나기 전에 해지 예약을 하지
            않으면 첫 결제가 진행되고, 결제가 되면 월 3,900원 멤버십으로 바뀌어요.
          </dd>
        </dl>
        <div className="promo__actions">
          <Link to="/membership-info" className="btn btn--large" onClick={onClose}>
            {onHideToday ? '멤버십 무료 체험하기' : '멤버십 자세히 보기'}
          </Link>
          <button type="button" className="btn btn--large btn--outline" onClick={onClose}>
            닫기
          </button>
        </div>
        {onHideToday && (
          <div className="promo__foot">
            <button type="button" className="promo__skip" onClick={onHideToday}>
              오늘 하루 안 보기
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
