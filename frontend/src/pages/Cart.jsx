import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { readSources } from '../cartSource';
import ConfirmModal from '../components/ConfirmModal';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import PendingOrderNotice from '../components/PendingOrderNotice';
import { ListSkeleton } from '../components/Skeleton';
import { CART_STATUS, formatAmount, formatWon } from '../labels';

export default function Cart() {
  const navigate = useNavigate();
  const { member } = useAuth();
  const [cart, setCart] = useState(null);
  // 상품별로 어느 레시피의 재료로 담았는지(자동 담기 때 기억해 둔 값)
  const [sources, setSources] = useState({});
  // 선택 항목은 서버에 저장되지 않아 화면에서만 들고 있는다
  const [selected, setSelected] = useState(new Set());
  const [error, setError] = useState(null);
  // 삭제 확인 창: 'selected' | 'all' | null
  const [deleting, setDeleting] = useState(null);
  const loadedOnce = useRef(false);

  const load = useCallback(async () => {
    try {
      const data = await api.get('/api/cart-items');
      setCart(data);
      setSources(readSources(member.memberId, data.items.map((item) => item.productId)));
      const firstLoad = !loadedOnce.current;
      loadedOnce.current = true;
      // 처음엔 주문 가능한 항목을 모두 선택하고, 이후엔 사용자가 고른 선택을 유지한다
      setSelected(
        (prev) =>
          new Set(
            data.items
              .filter((item) => item.status === 'AVAILABLE' && (firstLoad || prev.has(item.cartItemId)))
              .map((item) => item.cartItemId),
          ),
      );
    } catch (e) {
      setError(e);
    }
  }, [member.memberId]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (action) => {
    setError(null);
    try {
      await action();
      await load();
    } catch (e) {
      setError(e);
    }
  };

  const toggle = (id) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const removeMany = async () => {
    const targets = deleting === 'all' ? cart.items : cart.items.filter((item) => selected.has(item.cartItemId));
    setDeleting(null);
    if (targets.length === 0) return;
    // 한 번에 지울 수 있는 개수가 100개라 나눠서 보낸다
    await act(async () => {
      for (let i = 0; i < targets.length; i += 100) {
        await api.post('/api/cart-items/bulk-delete', { cartItemIds: targets.slice(i, i + 100).map((item) => item.cartItemId) });
      }
    });
  };

  if (!cart) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <ListSkeleton />}
      </div>
    );
  }

  const selectableIds = cart.items.filter((item) => item.status === 'AVAILABLE').map((item) => item.cartItemId);
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selected.has(id));
  const chosen = cart.items.filter((item) => selected.has(item.cartItemId));
  const productAmount = chosen.reduce((sum, item) => sum + item.lineAmount, 0);
  const { freeDeliveryThreshold, deliveryFee } = cart.deliveryPolicy;
  const fee = productAmount === 0 || productAmount >= freeDeliveryThreshold ? 0 : deliveryFee;

  // 레시피별로 묶는다. 여러 레시피에 쓰이는 상품은 먼저 담은 레시피 아래에 두고 나머지는 라벨로 알린다
  const groups = [];
  const groupByName = new Map();
  cart.items.forEach((item) => {
    const name = sources[item.productId]?.[0] ?? null;
    if (!groupByName.has(name)) {
      const group = { name, items: [] };
      groupByName.set(name, group);
      groups.push(group);
    }
    groupByName.get(name).items.push(item);
  });
  groups.sort((a, b) => (a.name === null) - (b.name === null));

  const renderItem = (item) => {
    const available = item.status === 'AVAILABLE';
    const recipes = sources[item.productId] ?? [];
    return (
      <div key={item.cartItemId} className="cart-row">
        <input
          type="checkbox"
          aria-label={`${item.productName} 선택`}
          disabled={!available}
          checked={selected.has(item.cartItemId)}
          onChange={() => toggle(item.cartItemId)}
        />
        {item.imageUrl ? <img className="cart-row__image" src={item.imageUrl} alt="" /> : <div className="cart-row__image" />}
        <div>
          {recipes.length > 0 && (
            <div className="chips" style={{ marginBottom: 4 }}>
              {recipes.map((name) => (
                <span key={name} className="badge">
                  {name}
                </span>
              ))}
            </div>
          )}
          {recipes.length > 1 && <div className="tile__meta">여러 레시피에 함께 쓰여요</div>}
          <div className="cart-row__name">{item.productName}</div>
          <div className="tile__meta">
            {formatAmount(item.saleUnitAmount, item.saleUnit)} · {formatWon(item.price)}
          </div>
          {!available && <span className="badge badge--warn">{CART_STATUS[item.status]}</span>}
        </div>
        <div className="cart-row__side">
          <span className="tile__price">{formatWon(item.lineAmount)}</span>
          <div className="stepper">
            {/* 재고가 모자란 항목은 재고 이하로 줄이는 변경만 허용한다 */}
            <button
              type="button"
              aria-label="수량 줄이기"
              disabled={item.quantity <= 1 || !(available || item.status === 'INSUFFICIENT_STOCK')}
              onClick={() => act(() => api.patch(`/api/cart-items/${item.cartItemId}`, { quantity: item.quantity - 1 }))}
            >
              −
            </button>
            <span>{item.quantity}</span>
            <button
              type="button"
              aria-label="수량 늘리기"
              disabled={!available || item.quantity >= Math.min(99, item.stock)}
              onClick={() => act(() => api.patch(`/api/cart-items/${item.cartItemId}`, { quantity: item.quantity + 1 }))}
            >
              +
            </button>
          </div>
          <button type="button" className="btn btn--outline" onClick={() => act(() => api.delete(`/api/cart-items/${item.cartItemId}`))}>
            삭제
          </button>
        </div>
      </div>
    );
  };

  return (
    <div className="container page">
      <h1 className="page__title">장바구니</h1>
      <ErrorNotice error={error} />
      <PendingOrderNotice onChanged={load} />
      {cart.hasPreviousMartItems && (
        <div className="notice">이전 마트 상품이 담겨 있습니다. 삭제한 뒤 주문할 수 있습니다.</div>
      )}

      {cart.items.length === 0 ? (
        <EmptyState
          icon="cart"
          title="장바구니가 비어 있어요"
          hint="레시피에서 재료를 한 번에 담거나, 상품을 직접 골라 담을 수 있어요."
          actions={[
            { to: '/recipes', label: '레시피 보러 가기' },
            { to: '/products', label: '상품 보러 가기' },
          ]}
        />
      ) : (
        <div className="cart">
          <div className="stack">
            <div className="card cart-toolbar">
              <label className="check">
                <input
                  type="checkbox"
                  checked={allSelected}
                  disabled={selectableIds.length === 0}
                  onChange={() => setSelected(allSelected ? new Set() : new Set(selectableIds))}
                />
                전체 선택 ({chosen.length}/{cart.items.length})
              </label>
              <span className="chips">
                <button type="button" className="chip" disabled={chosen.length === 0} onClick={() => setDeleting('selected')}>
                  선택 삭제
                </button>
                <button type="button" className="chip" onClick={() => setDeleting('all')}>
                  전체 삭제
                </button>
              </span>
            </div>
            {groups.map((group) => (
              <section key={group.name ?? 'direct'} className="card">
                <h2 className="tile__title cart-group__title">{group.name ? `${group.name} 재료` : '직접 담은 상품'}</h2>
                {group.items.map(renderItem)}
              </section>
            ))}
          </div>

          <aside className="card">
            <h2 className="tile__title">결제 금액</h2>
            <div className="summary__row">
              <span>상품 금액</span>
              <span>{formatWon(productAmount)}</span>
            </div>
            <div className="summary__row">
              <span>배송비</span>
              <span>{fee === 0 ? '무료' : formatWon(fee)}</span>
            </div>
            <div className="summary__row summary__total">
              <span>합계</span>
              <span>{formatWon(productAmount + fee)}</span>
            </div>
            <p className="field__hint">{formatWon(freeDeliveryThreshold)} 이상 주문하면 배송비가 무료입니다. 최종 금액은 주문 단계에서 다시 계산됩니다.</p>
            <button
              type="button"
              className="btn btn--large btn--block"
              style={{ marginTop: 16 }}
              disabled={chosen.length === 0}
              onClick={() => navigate('/checkout', { state: { cartItemIds: chosen.map((item) => item.cartItemId) } })}
            >
              주문하기
            </button>
          </aside>
        </div>
      )}
      {deleting && (
        <ConfirmModal
          title={deleting === 'all' ? '장바구니를 모두 비울까요?' : '선택한 상품을 삭제할까요?'}
          confirmLabel="삭제"
          cancelLabel="취소"
          onConfirm={removeMany}
          onCancel={() => setDeleting(null)}
        >
          {deleting === 'all' ? `담긴 상품 ${cart.items.length}개가 모두 삭제돼요.` : `선택한 상품 ${chosen.length}개가 삭제돼요.`}
        </ConfirmModal>
      )}
    </div>
  );
}
