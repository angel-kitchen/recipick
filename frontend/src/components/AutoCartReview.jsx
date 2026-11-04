import { useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, isUnknownOutcome } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { notifyCartChanged, rememberSources } from '../cartSource';
import { UNAVAILABLE_REASON, formatAmount, formatWon } from '../labels';
import ConfirmModal from './ConfirmModal';
import ErrorNotice from './ErrorNotice';
import usePreviousMartGuard from './usePreviousMartGuard';

const NONE = 'NONE';

// 서버가 만든 미리보기(preview)를 재료별로 고쳐서 장바구니에 담는다.
// - 체크를 빼거나 상품 목록에서 "선택 안 함"을 고르면 그 재료는 담지 않는다.
// - 서버의 확정 API가 선택·제외를 검증하고 장바구니와 확정 이력을 함께 저장한다.
export default function AutoCartReview({ preview, disabled = false }) {
  const navigate = useNavigate();
  const { member } = useAuth();
  const { guard, modal } = usePreviousMartGuard();
  // 재료별 선택. 값이 없거나 새 미리보기의 후보에 없으면 서버가 추천한 상품, NONE이면 담지 않음
  const [choices, setChoices] = useState({});
  const [added, setAdded] = useState(null);
  // 담으려는 상품 중 이미 장바구니에 있는 것들. 있으면 담기 전에 한 번 더 묻는다
  const [duplicates, setDuplicates] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [outcomeUnknown, setOutcomeUnknown] = useState(false);
  // 장바구니를 읽는 중. 버튼을 연달아 눌러도 한 번만 시작하도록 ref로 먼저 막는다
  const [checking, setChecking] = useState(false);
  const busyRef = useRef(false);

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
    return {
      item,
      productId: Number(productId),
      name: candidate.name,
      imageUrl: candidate.imageUrl ?? null,
      quantity,
      price: candidate.price * quantity,
    };
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

  const doAdd = async (targets) => {
    setError(null);
    setSubmitting(true);
    try {
      const result = await api.post(`/api/auto-carts/${preview.autoCartRequestId}/confirm`, {
        productSelections: targets.map((line) => ({ ingredientId: line.item.ingredientId, productId: line.productId })),
        excludedIngredientIds: preview.items.filter((item) => choices[item.ingredientId] === NONE).map((item) => item.ingredientId),
      });
      const done = result.addedItems.map((item) => ({
        ...targets.find((line) => line.productId === item.productId),
        productId: item.productId, name: item.name, quantity: item.addedQuantity,
        total: item.cartQuantity, price: item.linePrice,
      }));
      rememberSources(member.memberId, done.map((line) => line.productId), menuLabels);
      notifyCartChanged();
      setAdded({ lines: done, priceChanged: result.priceChanged, excluded: result.excludedItems });
    } catch (e) {
      if (isUnknownOutcome(e)) {
        setOutcomeUnknown(true);
        setError(new Error('확정 결과를 확인하지 못했어요. 장바구니나 자동 장보기 이력에서 반영 여부를 먼저 확인해 주세요.'));
      } else {
        setError(e);
      }
    } finally {
      setSubmitting(false);
      busyRef.current = false;
    }
  };

  // 서버는 같은 상품을 새 줄로 만들지 않고 수량을 더하므로, 담기 전에 이미 있는 상품을 알려 주고 확인받는다.
  // 장바구니를 읽지 못하면 중복 여부를 모르므로 담지 않고 오류를 보여 준다(다시 누르면 재시도).
  // 누른 시점의 목록(targets)을 고정해, 확인 팝업에 보인 상품만 담는다
  const startAdd = async () => {
    if (busyRef.current) return;
    busyRef.current = true;
    setChecking(true);
    setError(null);
    const targets = picked;
    let found;
    try {
      const cart = await api.get('/api/cart-items');
      found = targets.flatMap((line) => {
        const inCart = cart.items.find((item) => item.productId === line.productId);
        return inCart ? [{ ...line, current: inCart.quantity }] : [];
      });
    } catch (e) {
      setError(e);
      busyRef.current = false;
      setChecking(false);
      return;
    }
    setChecking(false);
    if (found.length > 0) setDuplicates({ targets, found });
    else await doAdd(targets);
  };

  const confirmDuplicates = () => {
    const { targets } = duplicates;
    setDuplicates(null);
    doAdd(targets);
  };

  const cancelDuplicates = () => {
    setDuplicates(null);
    busyRef.current = false;
  };

  // 조회·확인·담기가 끝날 때까지 선택을 바꾸지 못하게 한다
  const locked = checking || submitting || duplicates !== null || added !== null || outcomeUnknown || preview.status === 'CONFIRMED';

  const orderNow = async () => {
    setError(null);
    try {
      const cart = await api.get('/api/cart-items');
      const ids = new Set(added.lines.map((line) => line.productId));
      const cartItemIds = cart.items
        .filter((item) => ids.has(item.productId) && item.status === 'AVAILABLE')
        .map((item) => item.cartItemId);
      if (cartItemIds.length === 0) throw new Error('주문할 수 있는 상품이 없어요. 장바구니를 확인해 주세요.');
      navigate('/checkout', { state: { cartItemIds } });
    } catch (e) {
      setError(e);
    }
  };

  return (
    <div>
      <ErrorNotice error={error} />
      {(outcomeUnknown || preview.status === 'CONFIRMED') && <p className="notice"><Link to="/cart">장바구니 확인</Link> · <Link to="/auto-carts">자동 장보기 이력</Link></p>}
      <div className="review-summary">
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
      </div>
      <div className="review">
        {preview.items.map((item) => {
          const productId = item.purchasable ? chosenProductId(item) : null;
          const excluded = productId === NONE;
          // 드롭다운에서 고른 상품의 사진. 후보에 없으면(선택 안 함 등) 기본 상품 사진을 쓴다
          const chosen = item.candidates.find((c) => String(c.productId) === String(productId));
          const imageUrl = chosen?.imageUrl ?? item.product?.imageUrl;
          return (
            <div key={item.ingredientId} className={`review__row ${excluded || !item.purchasable ? 'is-off' : ''}`}>
              <input
                type="checkbox"
                aria-label={`${item.ingredientName} 담기`}
                disabled={!item.purchasable || locked}
                checked={item.purchasable && !excluded}
                onChange={() => toggle(item)}
              />
              {imageUrl ? <img className="cart-row__image" src={imageUrl} alt="" /> : <div className="cart-row__image" />}
              <div>
                <div className="cart-row__name">{item.ingredientName}</div>
                <div className="tile__meta">필요량 {formatAmount(item.totalRequiredAmount, item.unit)}</div>
              </div>
              <div>
                {item.purchasable ? (
                  <>
                    <select
                      className="field__input"
                      disabled={locked}
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

      <button type="button" className="btn btn--large btn--block" style={{ marginTop: 12 }} disabled={disabled || locked || picked.length === 0} onClick={() => guard(startAdd)}>
        {submitting ? '담는 중...' : checking ? '장바구니 확인 중...' : `${picked.length}개 장바구니에 담기`}
      </button>

      {modal}
      {duplicates && (
        <ConfirmModal
          title="이미 장바구니에 중복된 재료가 있어요"
          confirmLabel="또 담기"
          cancelLabel="취소"
          onConfirm={confirmDuplicates}
          onCancel={cancelDuplicates}
        >
          또 담으시겠어요? 같은 상품은 새로 담지 않고 수량이 합쳐져요.
          <ul className="list-plain" style={{ margin: '12px 0 0' }}>
            {duplicates.found.map((line) => (
              <li key={line.productId}>
                <span>{line.name}</span>
                <span>
                  {line.current}개 → {line.current + line.quantity}개
                </span>
              </li>
            ))}
          </ul>
        </ConfirmModal>
      )}
      {added && (
        <div className="modal" role="dialog" aria-modal="true" aria-label="장바구니에 담겼어요">
          <div className="modal__box card modal__box--center">
            <button type="button" className="modal__close" aria-label="닫기" onClick={() => setAdded(null)}>
              ×
            </button>
            <h2 className="tile__title">장바구니에 담겼어요</h2>
            {added.priceChanged && <p className="notice">선택 내용과 최신 가격을 반영한 최종 금액이에요.</p>}
            {added.excluded?.map((item) => <p key={item.ingredientId} className="notice">{item.ingredientName}: {UNAVAILABLE_REASON[item.reason] ?? item.reason}</p>)}
            <p className="field__hint">
              {menuLabels.join(', ')} 재료 {added.lines.length}개
            </p>
            <ul className="list-plain" style={{ margin: '12px 0' }}>
              {added.lines.map((line) => (
                <li key={line.productId}>
                  <span className="added__name">
                    {line.imageUrl ? <img className="added__image" src={line.imageUrl} alt="" /> : <span className="added__image" />}
                    <span>
                      {line.name} × {line.quantity}
                      {line.total > line.quantity && (
                        <span className="tile__meta" style={{ display: 'block' }}>
                          이미 담겨 있어서 합쳐서 총 {line.total}개가 됐어요
                        </span>
                      )}
                    </span>
                  </span>
                  <span>{formatWon(line.price)}</span>
                </li>
              ))}
            </ul>
            <ErrorNotice error={error} />
            <div className="row-actions row-actions--center">
              <button type="button" className="btn btn--large" onClick={orderNow}>
                바로 주문하기
              </button>
              <Link to="/cart" className="btn btn--large btn--outline">
                장바구니로 가기
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
