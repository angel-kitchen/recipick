import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { deliveryBlock } from '../delivery';
import AutoCartReview from './AutoCartReview';
import ErrorNotice from './ErrorNotice';
import { ListSkeleton } from './Skeleton';

// 상세로 들어가지 않고 카드에서 1인분 재료를 바로 확인해 담는다. 멤버십·배송지·자동 담기 설정이 없으면 이유를 알려 주고 막는다
// 버튼 모양은 놓이는 곳마다 달라서 className으로 받는다(기본은 레시피 목록 카드 위에 겹치는 모양)
export default function QuickAdd({ recipe, className = 'btn btn--outline quick-add' }) {
  const { member } = useAuth();
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  // 로그아웃 직후 화면이 바뀌기 전 한 번 그려질 때 member가 비어 있을 수 있다
  if (!member) return null;

  const blockedText =
    deliveryBlock(member)?.text ??
    (!member.membership?.active
      ? '멤버십 회원만 이용할 수 있어요'
      : !recipe.autoCartAvailable
        ? '아직 자동으로 담을 수 없는 레시피예요'
        : null);

  const start = async () => {
    setError(null);
    setPreview(null);
    setOpen(true);
    setLoading(true);
    try {
      setPreview(
        await api.post('/api/auto-carts/preview', {
          menus: [{ recipeId: recipe.recipeId, servings: 1, optionalIngredientIds: [] }],
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <button
        type="button"
        className={className}
        disabled={Boolean(blockedText)}
        title={blockedText ?? undefined}
        aria-label={blockedText ? `재료 담기, ${blockedText}` : undefined}
        onClick={start}
      >
        재료 담기
      </button>
      {open && (
        <div className="modal" role="dialog" aria-modal="true" aria-label={`${recipe.name} 재료 담기`}>
          <div className="modal__box modal__box--wide card">
            <button type="button" className="modal__close" aria-label="닫기" onClick={() => setOpen(false)}>
              ×
            </button>
            <h2 className="tile__title">{recipe.name} 1인분 재료</h2>
            <ErrorNotice error={error} />
            {loading && <ListSkeleton rows={4} />}
            {preview && <AutoCartReview preview={preview} onBrowse={() => setOpen(false)} />}
            <div className="row-actions" style={{ marginTop: 12 }}>
              <Link to={`/recipes/${recipe.recipeId}`} className="btn btn--outline">
                인분·선택 재료 바꾸기
              </Link>
              <button type="button" className="btn btn--outline row-actions__end" onClick={() => setOpen(false)}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
