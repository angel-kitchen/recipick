import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { rememberSources } from '../cartSource';
import { UNAVAILABLE_REASON, formatAmount, formatWon } from '../labels';
import ErrorNotice from './ErrorNotice';
import usePreviousMartGuard from './usePreviousMartGuard';

const NONE = 'NONE';

// 서버가 만든 미리보기(preview)를 재료별로 고쳐서 장바구니에 담는다.
// - 체크를 빼거나 상품 목록에서 "선택 안 함"을 고르면 그 재료는 담지 않는다.
// - 담기는 선택한 상품을 장바구니 담기 API로 하나씩 넣는다(재료를 뺄 수 있게 하려는 방식).
export default function AutoCartReview({ preview, disabled = false }) {
  const navigate = useNavigate();
  const { member } = useAuth();
  const { guard, modal } = usePreviousMartGuard();
  // 재료별 선택. 값이 없거나 새 미리보기의 후보에 없으면 서버가 추천한 상품, NONE이면 담지 않음
  const [choices, setChoices] = useState({});
  const [added, setAdded] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // 장바구니 묶음 이름. 같은 레시피라도 인분이 다르면 따로 보인다
  const menuLabels = preview.menus.map((menu) => `${menu.name} ${menu.servings}인분`);

  const chosenProductId = (item) => {
    const choice = choices[item.ingredientId];
    if (choice === NONE) return NONE;
    if (choice && item.candidates.some((c) => String(c.productId) === choice)) return choice;
    return String(item.product?.productId);
  };

  const quantityOf = (item, productId) => {
    if (Number(productId) === item.product?.productId) return item.purchaseQuantity;
    const candidate = item.candidates.find((c) => c.productId === Number(productId));
    return Math.min(99, Math.max(1, Math.ceil(Number(item.totalRequiredAmount) / Number(candidate.saleUnitAmount))));
  };

  const lineOf = (item) => {
    const productId = chosenProductId(item);
    if (productId === NONE) return null;
    const candidate = item.candidates.find((c) => c.productId === Number(productId)) ?? item.product;
    const quantity = quantityOf(item, productId);
    return { item, productId: Number(productId), name: candidate.name, quantity, price: candidate.price * quantity };
  };

  const lines = preview.items.filter((item) => item.purchasable).map(lineOf);
  const picked = lines.filter(Boolean);
  const total = picked.reduce((sum, line) => sum + line.price, 0);
  const unavailable = preview.items.filter((item) => !item.purchasable);
  const skipped = lines.length - picked.length;

  const toggle = (item) =>
    setChoices((prev) => {
      const next = { ...prev };
      if (chosenProductId(item) === NONE) delete next[item.ingredientId];
      else next[item.ingredientId] = NONE;
      return next;
    });

  const doAdd = async () => {
    setError(null);
    setSubmitting(true);
    const done = [];
    try {
      for (const line of picked) {
        const res = await api.post('/api/cart-items', { productId: line.productId, quantity: line.quantity });
        // 서버는 같은 상품을 새 줄로 만들지 않고 수량을 더한다. 합친 총 수량을 받아 두었다가 알려 준다
        done.push({ ...line, total: res?.quantity ?? line.quantity });
      }
      rememberSources(member.memberId, done.map((line) => line.productId), menuLabels);
      setAdded({ lines: done });
    } catch (e) {
      // 일부만 담겼을 수 있어 담긴 만큼은 기억해 장바구니에서 레시피가 보이게 한다
      rememberSources(member.memberId, done.map((line) => line.productId), menuLabels);
      setError(
        done.length > 0 ? new Error(`${e.message} (앞의 ${done.length}개는 장바구니에 담겼습니다. 장바구니를 확인해 주세요.)`) : e,
      );
    } finally {
      setSubmitting(false);
    }
  };

  const orderNow = async () => {
    setError(null);
    try {
      const cart = await api.get('/api/cart-items');
      const ids = new Set(added.lines.map((line) => line.productId));
      const cartItemIds = cart.items
        .filter((item) => ids.has(item.productId) && item.status === 'AVAILABLE')
        .map((item) => item.cartItemId);
      if (cartItemIds.length === 0) throw new Error('주문할 수 있는 상품이 없습니다. 장바구니를 확인해 주세요.');
      navigate('/checkout', { state: { cartItemIds } });
    } catch (e) {
      setError(e);
    }
  };

  return (
    <div>
      <ErrorNotice error={error} />
      <div className="review">
        {preview.items.map((item) => {
          const productId = item.purchasable ? chosenProductId(item) : null;
          const excluded = productId === NONE;
          return (
            <div key={item.ingredientId} className={`review__row ${excluded || !item.purchasable ? 'is-off' : ''}`}>
              <input
                type="checkbox"
                aria-label={`${item.ingredientName} 담기`}
                disabled={!item.purchasable}
                checked={item.purchasable && !excluded}
                onChange={() => toggle(item)}
              />
              <div>
                <div className="cart-row__name">{item.ingredientName}</div>
                <div className="tile__meta">필요량 {formatAmount(item.totalRequiredAmount, item.unit)}</div>
              </div>
              <div>
                {item.purchasable ? (
                  <>
                    <select
                      className="field__input"
                      value={productId}
                      onChange={(e) => setChoices((prev) => ({ ...prev, [item.ingredientId]: e.target.value }))}
                    >
                      {item.candidates.map((candidate) => (
                        <option key={candidate.productId} value={candidate.productId}>
                          {candidate.name} · {formatWon(candidate.price)}
                        </option>
                      ))}
                      <option value={NONE}>선택 안 함 (집에 있어요)</option>
                    </select>
                    {!excluded && (
                      <div className="tile__meta">
                        {quantityOf(item, productId)}개
                      </div>
                    )}
                  </>
                ) : (
                  <span className="badge badge--warn">{UNAVAILABLE_REASON[item.unavailableReason] ?? item.unavailableReason}</span>
                )}
              </div>
              <span className="tile__price">
                {item.purchasable && !excluded ? formatWon(lineOf(item).price) : '-'}
              </span>
            </div>
          );
        })}
      </div>

      <div className="summary__row">
        <span>담을 상품</span>
        <span>{picked.length}개</span>
      </div>
      {(skipped > 0 || unavailable.length > 0) && (
        <div className="summary__row">
          <span>담지 않는 재료</span>
          <span>{skipped + unavailable.length}개</span>
        </div>
      )}
      <div className="summary__row summary__total">
        <span>예상 금액</span>
        <span>{formatWon(total)}</span>
      </div>
      <p className="field__hint">미리보기는 저장되지 않아요. 담을 때 가격과 재고를 다시 확인해요.</p>
      <button type="button" className="btn btn--large btn--block" style={{ marginTop: 12 }} disabled={disabled || submitting || picked.length === 0} onClick={() => guard(doAdd)}>
        {submitting ? '담는 중...' : `${picked.length}개 장바구니에 담기`}
      </button>

      {modal}
      {added && (
        <div className="modal" role="dialog" aria-modal="true" aria-label="장바구니에 담겼습니다">
          <div className="modal__box card">
            <h2 className="tile__title">장바구니에 담겼습니다</h2>
            <p className="field__hint">
              {menuLabels.join(', ')} 재료 {added.lines.length}개
            </p>
            <ul className="list-plain" style={{ margin: '12px 0' }}>
              {added.lines.map((line) => (
                <li key={line.productId}>
                  <span>
                    {line.name} × {line.quantity}
                    {line.total > line.quantity && (
                      <span className="tile__meta" style={{ display: 'block' }}>
                        이미 담겨 있어서 합쳐서 총 {line.total}개가 됐어요
                      </span>
                    )}
                  </span>
                  <span>{formatWon(line.price)}</span>
                </li>
              ))}
            </ul>
            <ErrorNotice error={error} />
            <div className="row-actions">
              <button type="button" className="btn btn--large" onClick={orderNow}>
                바로 주문하기
              </button>
              <Link to="/cart" className="btn btn--large btn--outline">
                장바구니로 가기
              </Link>
            </div>
            <button type="button" className="header__link-button" style={{ marginTop: 12 }} onClick={() => setAdded(null)}>
              닫기
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
