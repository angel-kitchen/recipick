import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import ConfirmModal from '../components/ConfirmModal';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { CardSkeletons } from '../components/Skeleton';
import { CUISINE } from '../labels';

const PAGE_SIZE = 12;

export default function Bookmarks() {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  // 해제 확인 창에 띄운 레시피
  const [removing, setRemoving] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let ignore = false;
    api
      .get('/api/members/me/recipe-bookmarks', { page, size: PAGE_SIZE })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [page]);

  // 해제한 레시피는 목록에서 바로 뺀다. 한 쪽이 비면 이전 쪽으로 돌아간다
  const remove = async () => {
    setBusy(true);
    try {
      await api.delete(`/api/recipes/${removing.recipeId}/bookmarks`);
      const content = result.content.filter((recipe) => recipe.recipeId !== removing.recipeId);
      if (content.length === 0 && page > 0) setPage(page - 1);
      else setResult({ ...result, content, totalElements: result.totalElements - 1 });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
      setRemoving(null);
    }
  };

  return (
    <div className="container page">
      <h1 className="page__title">찜한 레시피</h1>
      <ErrorNotice error={error} />
      {!result && !error && <CardSkeletons />}
      {result?.content.length === 0 && (
        <EmptyState
          icon="heart"
          title="찜한 레시피가 없어요"
          hint="마음에 드는 레시피의 하트를 눌러 두면 여기에 모여요."
          actions={[{ to: '/recipes', label: '레시피 보러 가기' }]}
        />
      )}
      <div className="grid">
        {result?.content.map((recipe) => (
          <div key={recipe.recipeId} className="tile-wrap">
            <Link to={`/recipes/${recipe.recipeId}`} className="tile">
              {recipe.thumbnailUrl ? (
                <img className="tile__image" src={recipe.thumbnailUrl} alt="" loading="lazy" />
              ) : (
                <div className="tile__image">이미지 준비 중</div>
              )}
              <div className="tile__body">
                <span className="tile__title">{recipe.name}</span>
                <span className="tile__meta">{CUISINE[recipe.cuisineType]}</span>
                {!recipe.autoCartAvailable && <span className="badge badge--warn">자동 담기 준비 중</span>}
              </div>
            </Link>
            <button type="button" className="bookmark-btn is-on" aria-label="찜 해제" onClick={() => setRemoving(recipe)}>
              ♥
            </button>
          </div>
        ))}
      </div>
      {result && <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />}
      {removing && (
        <ConfirmModal title="찜 목록에서 제거하시겠어요?" confirmLabel="제거" cancelLabel="취소" busy={busy} onConfirm={remove} onCancel={() => setRemoving(null)}>
          {removing.name}을(를) 찜 목록에서 뺍니다.
        </ConfirmModal>
      )}
    </div>
  );
}
