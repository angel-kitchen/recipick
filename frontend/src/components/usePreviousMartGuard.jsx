import { useState } from 'react';
import { clearPreviousMartItems, hasPreviousMartItems } from '../api/cart';
import ConfirmModal from './ConfirmModal';

// 다른 마트 상품이 담겨 있으면 담기 전에 지울지 묻는다. run은 실제로 담는 동작이다
export default function usePreviousMartGuard() {
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const guard = async (run) => {
    let previous = false;
    try {
      previous = await hasPreviousMartItems();
    } catch {
      // 확인에 실패하면 담기 요청이 서버 검증에 맡긴다
    }
    if (previous) {
      setError(null);
      setPending(() => run);
    } else {
      await run();
    }
  };

  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await clearPreviousMartItems();
      const run = pending;
      setPending(null);
      await run();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const modal = pending && (
    <ConfirmModal
      title="이전 마트 상품이 담겨 있어요"
      confirmLabel="삭제하고 담기"
      cancelLabel="취소"
      busy={busy}
      error={error}
      onConfirm={confirm}
      onCancel={() => setPending(null)}
    >
      배송지가 바뀌기 전 마트의 상품이 장바구니에 남아 있어요. 새 상품을 담으려면 먼저 삭제해야 해요. 기존 상품을 삭제할까요?
    </ConfirmModal>
  );

  return { guard, modal };
}
