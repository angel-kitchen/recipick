import { useCallback, useEffect, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { cancelPendingOrder, findPendingOrder } from '../api/orders';
import { useAuth } from '../auth/AuthContext';
import ConfirmModal from '../components/ConfirmModal';
import ErrorNotice from '../components/ErrorNotice';
import PendingOrderNotice from '../components/PendingOrderNotice';
import { CART_STATUS, UNORDERABLE_REASON, formatWon } from '../labels';

export default function Checkout() {
  const { member } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const cartItemIds = location.state?.cartItemIds;
  const address = member.currentAddress;

  const [form, setForm] = useState({
    receiverName: member.name ?? '',
    receiverPhone: member.phone ?? '',
    address: address?.address ?? '',
    detailAddress: address?.detailAddress ?? '',
    legalDongCode: address?.legalDongCode ?? '',
  });
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  // 'pending': 결제 대기 주문 안내, 'address': 배송지 변경 확인
  const [modal, setModal] = useState(null);
  const [pendingOrder, setPendingOrder] = useState(null);
  const [modalBusy, setModalBusy] = useState(false);
  const [modalError, setModalError] = useState(null);
  // 같은 내용으로 다시 보낼 땐 같은 키를 써서 주문이 두 번 만들어지지 않게 한다
  const idempotency = useRef({ body: null, key: null });

  const runPreview = useCallback(async () => {
    setError(null);
    try {
      setPreview(
        await api.post('/api/orders/preview', {
          cartItemIds,
          legalDongCode: form.legalDongCode || null,
        }),
      );
    } catch (e) {
      setPreview(null);
      setError(e);
    }
  }, [cartItemIds, form.legalDongCode]);

  useEffect(() => {
    if (cartItemIds) runPreview();
    // 처음 열 때와 "다시 확인"을 눌렀을 때만 조회한다
  }, []);

  if (!cartItemIds) return <Navigate to="/cart" replace />;

  const change = (key) => (event) => setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const placeOrder = async () => {
    setError(null);
    setSubmitting(true);
    const body = {
      cartItemIds,
      receiverName: form.receiverName,
      receiverPhone: form.receiverPhone.replace(/-/g, ''),
      address: form.address,
      detailAddress: form.detailAddress,
      legalDongCode: form.legalDongCode || null,
    };
    const serialized = JSON.stringify(body);
    if (idempotency.current.body !== serialized) {
      idempotency.current = { body: serialized, key: crypto.randomUUID() };
    }
    try {
      const order = await api.post('/api/orders', body, { 'Idempotency-Key': idempotency.current.key });
      navigate(`/orders/${order.orderId}`, { replace: true });
    } catch (e) {
      if (e.code === 'ORDER-003') {
        // 결제하지 않은 주문이 남아 있으면 오류 문구 대신 취소를 안내하는 창을 띄운다
        setPendingOrder(await findPendingOrder().catch(() => null));
        setModalError(null);
        setModal('pending');
      } else {
        setError(e);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    placeOrder();
  };

  const cancelPendingAndRetry = async () => {
    setModalBusy(true);
    setModalError(null);
    try {
      if (pendingOrder) await cancelPendingOrder(pendingOrder.orderId);
      setModal(null);
      await placeOrder();
    } catch (e) {
      setModalError(e);
    } finally {
      setModalBusy(false);
    }
  };

  return (
    <div className="container page">
      <h1 className="page__title">주문하기</h1>
      <ErrorNotice error={error} />
      <PendingOrderNotice onChanged={runPreview} />
      <div className="cart">
        <form className="card form" onSubmit={handleSubmit} id="order-form">
          <h2 className="tile__title">배송 정보</h2>
          <label className="field">
            <span className="field__label">받는 분</span>
            <input className="field__input" value={form.receiverName} onChange={change('receiverName')} maxLength={50} required />
          </label>
          <label className="field">
            <span className="field__label">연락처</span>
            <input className="field__input" inputMode="numeric" value={form.receiverPhone} onChange={change('receiverPhone')} required />
          </label>
          {/* 주소와 지역 코드는 한 쌍이라 직접 고치지 않고 배송지 화면에서 바꾼다 */}
          <label className="field">
            <span className="field__label">배송지</span>
            <input className="field__input" value={form.address} readOnly placeholder="선택된 배송지가 없습니다" required />
            <span className="field__hint">
              <button type="button" className="header__link-button" onClick={() => setModal('address')}>
                배송지 변경
              </button>
            </span>
          </label>
          <label className="field">
            <span className="field__label">상세 주소</span>
            <input className="field__input" value={form.detailAddress} onChange={change('detailAddress')} maxLength={255} />
          </label>
          <button type="button" className="btn btn--outline" onClick={runPreview}>
            배송 가능 여부 다시 확인
          </button>
        </form>

        <aside className="card">
          <h2 className="tile__title">주문 요약</h2>
          {!preview && !error && <p className="field__hint">불러오는 중입니다.</p>}
          {preview && (
            <>
              {preview.delivery.status === 'AVAILABLE' && (
                <p className="field__hint">배송 마트: {preview.delivery.martName}</p>
              )}
              {preview.unorderableReasons.map((reason) => (
                <div key={reason} className="notice">
                  {UNORDERABLE_REASON[reason] ?? reason}
                </div>
              ))}
              <ul className="list-plain">
                {preview.items.map((item) => (
                  <li key={item.cartItemId}>
                    <span>
                      {item.productName} × {item.quantity}
                      {item.status !== 'AVAILABLE' && ` (${CART_STATUS[item.status]})`}
                    </span>
                    <span>{formatWon(item.lineAmount)}</span>
                  </li>
                ))}
              </ul>
              <div className="summary__row">
                <span>상품 금액</span>
                <span>{formatWon(preview.productAmount)}</span>
              </div>
              <div className="summary__row">
                <span>배송비</span>
                <span>{preview.deliveryFee === 0 ? '무료' : formatWon(preview.deliveryFee)}</span>
              </div>
              <div className="summary__row summary__total">
                <span>결제 금액</span>
                <span>{formatWon(preview.payAmount)}</span>
              </div>
            </>
          )}
          <button
            type="submit"
            form="order-form"
            className="btn btn--large btn--block"
            style={{ marginTop: 16 }}
            disabled={submitting || !preview?.orderable}
          >
            {submitting ? '결제 준비 중...' : '결제하기'}
          </button>
        </aside>
      </div>
      {modal === 'pending' && (
        <ConfirmModal
          title="결제하지 않은 주문이 있어요"
          confirmLabel="그 주문 취소하고 계속하기"
          busy={modalBusy}
          error={modalError}
          onConfirm={cancelPendingAndRetry}
          onCancel={() => setModal(null)}
        >
          {pendingOrder ? `${pendingOrder.orderName} · ${formatWon(pendingOrder.payAmount)}` : '결제 대기 중인 주문'}이 남아 있어서 새 주문을 만들 수 없어요. 결제하지 않은 주문을 취소하면 재고가 돌아오고 바로 이어서 결제할 수 있어요.
        </ConfirmModal>
      )}
      {modal === 'address' && (
        <ConfirmModal
          title="배송지를 변경하시겠어요?"
          confirmLabel="예"
          cancelLabel="아니요"
          onConfirm={() => navigate('/recipes')}
          onCancel={() => setModal(null)}
        >
          배송지 변경 시 주문 가능한 상품을 다시 담아야 해요. 레시피 첫 화면으로 이동하고, 왼쪽 위의 배송지에서 바꿀 수 있어요.
        </ConfirmModal>
      )}
    </div>
  );
}
