import { useState } from 'react';
import { api } from '../api/client';
import { useToast } from './Toast';

// 찜 추가는 POST, 해제는 DELETE다. 서버 결과가 오기 전에는 하트를 바꾸지 않아 실패했을 때 화면이 어긋나지 않는다
export default function BookmarkButton({ recipeId, bookmarked, onChange, onError }) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const toggle = async (event) => {
    // 카드 전체가 링크라서 하트를 눌러도 상세로 넘어가지 않게 한다
    event.preventDefault();
    event.stopPropagation();
    if (busy) return;
    setBusy(true);
    try {
      if (bookmarked) await api.delete(`/api/recipes/${recipeId}/bookmarks`);
      else await api.post(`/api/recipes/${recipeId}/bookmarks`);
      onChange(!bookmarked);
      toast.show(bookmarked ? '찜을 해제했어요.' : '찜했어요.', bookmarked ? undefined : { to: '/bookmarks', label: '찜 목록 보기' });
    } catch (e) {
      onError?.(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      className={`bookmark-btn ${bookmarked ? 'is-on' : ''}`}
      aria-label={bookmarked ? '찜 해제' : '찜하기'}
      aria-pressed={bookmarked}
      disabled={busy}
      onClick={toggle}
    >
      {bookmarked ? '♥' : '♡'}
    </button>
  );
}
