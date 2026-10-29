import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AutoCartReview from '../components/AutoCartReview';
import ErrorNotice from '../components/ErrorNotice';
import { CUISINE, DIFFICULTY, DISH_TYPE, formatAmount } from '../labels';

// watch?v=, youtu.be/, embed/, shorts/ 주소에서 11자리 영상 ID를 꺼낸다. 형식이 다르면 null이라 임베드하지 않는다
function youtubeId(url) {
  const match = url?.match(/(?:v=|youtu\.be\/|embed\/|shorts\/)([A-Za-z0-9_-]{11})/);
  return match ? match[1] : null;
}

// 자동 장바구니 패널은 쓸 수 없는 경우에도 숨기지 않고 이유를 보여 준다
function blockedReason(recipe, member) {
  if (!member.mart) return { text: '배송지를 등록하면 이용 마트의 상품으로 재료를 계산해 드려요.', to: '/addresses', label: '배송지 등록' };
  if (!member.membership?.active) return { text: '자동 장바구니는 멤버십 회원만 이용할 수 있어요.' };
  if (!recipe.autoCartAvailable) {
    return { text: '이 레시피는 재료와 상품 연결이 끝나지 않아 아직 자동으로 담을 수 없어요. 재료를 눌러 직접 담아 주세요.' };
  }
  return null;
}

function AutoCartPanel({ recipe, member }) {
  const [optionalIds, setOptionalIds] = useState(new Set());
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const blocked = blockedReason(recipe, member);
  const optionals = recipe.ingredients.filter((ingredient) => !ingredient.required);

  // 선택 재료를 빠르게 여러 번 누를 때 늦게 온 이전 응답이 최신 미리보기를 덮지 않게 마지막 요청만 반영한다
  const latestRequest = useRef(0);

  // 인분이 바뀌면 이전 미리보기는 맞지 않는다. 선택 재료는 미리보기를 접지 않고 toggleOptional에서 다시 계산한다
  useEffect(() => {
    latestRequest.current += 1;
    setPreview(null);
    setLoading(false);
  }, [recipe.servings, recipe.recipeId]);

  const makePreview = async (ids) => {
    const requestId = ++latestRequest.current;
    setError(null);
    setLoading(true);
    try {
      const result = await api.post('/api/auto-carts/preview', {
        menus: [{ recipeId: recipe.recipeId, servings: recipe.servings, optionalIngredientIds: [...ids] }],
      });
      if (requestId !== latestRequest.current) return;
      setPreview(result);
    } catch (e) {
      if (requestId !== latestRequest.current) return;
      setError(e);
      return false;
    } finally {
      if (requestId === latestRequest.current) setLoading(false);
    }
    return true;
  };

  const toggleOptional = async (ingredientId) => {
    const prev = optionalIds;
    const next = new Set(prev);
    if (next.has(ingredientId)) next.delete(ingredientId);
    else next.add(ingredientId);
    setOptionalIds(next);
    if (!preview) return;
    // 미리보기가 열려 있으면 접지 않고 그 자리에서 새 재료를 반영한다. 실패하면 체크를 되돌려 화면과 미리보기를 맞춘다
    if ((await makePreview(next)) === false) setOptionalIds(prev);
  };

  return (
    <aside className="card recipe-panel">
      <h2 className="tile__title">재료 자동 담기</h2>
      <p className="field__hint">
        {recipe.name} {recipe.servings}인분에 필요한 재료를 이용 마트 상품으로 골라 드려요. 담기 전에 빼거나 바꿀 수 있어요.
      </p>

      {blocked ? (
        <>
          <div className="notice notice--info" style={{ marginTop: 12 }}>
            {blocked.text}{' '}
            {blocked.to && <Link to={blocked.to}>{blocked.label}</Link>}
          </div>
          <button type="button" className="btn btn--large btn--block" disabled>
            재료 미리보기
          </button>
        </>
      ) : (
        <>
          {optionals.length > 0 && (
            <div style={{ margin: '12px 0' }}>
              <span className="field__label">선택 재료도 담기</span>
              {optionals.map((ingredient) => (
                <label key={ingredient.ingredientId} className="check">
                  <input
                    type="checkbox"
                    checked={optionalIds.has(ingredient.ingredientId)}
                    disabled={loading}
                    onChange={() => toggleOptional(ingredient.ingredientId)}
                  />
                  {ingredient.name}
                </label>
              ))}
            </div>
          )}
          <ErrorNotice error={error} />
          {!preview ? (
            <button type="button" className="btn btn--large btn--block" style={{ marginTop: 12 }} disabled={loading} onClick={() => makePreview(optionalIds)}>
              {loading ? '계산하는 중...' : '재료 미리보기'}
            </button>
          ) : (
            <div style={{ marginTop: 12 }}>
              <AutoCartReview preview={preview} disabled={loading} />
            </div>
          )}
        </>
      )}
    </aside>
  );
}

export default function RecipeDetail() {
  const { recipeId } = useParams();
  const [servings, setServings] = useState(null);
  const [recipe, setRecipe] = useState(null);
  const [error, setError] = useState(null);
  const { member } = useAuth();

  useEffect(() => {
    let ignore = false;
    setError(null);
    api
      .get(`/api/recipes/${recipeId}`, { servings })
      .then((data) => !ignore && setRecipe(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [recipeId, servings]);

  if (!recipe) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <p className="state-message">불러오는 중입니다.</p>}
      </div>
    );
  }

  const videoId = youtubeId(recipe.youtubeUrl);

  return (
    <div className="container page">
      <h1 className="page__title">{recipe.name}</h1>
      <div className="detail__facts">
        <span className="badge">{CUISINE[recipe.cuisineType]}</span>
        <span className="badge">{DISH_TYPE[recipe.dishType]}</span>
        {recipe.difficulty && <span className="badge">{DIFFICULTY[recipe.difficulty]}</span>}
        {recipe.cookingTimeMinutes && <span className="badge">{recipe.cookingTimeMinutes}분</span>}
      </div>

      <ErrorNotice error={error} />

      <div className="recipe-layout">
        <div className="stack">
          {videoId && (
            <section className="card">
              <div className="video-embed">
                <iframe
                  src={`https://www.youtube-nocookie.com/embed/${videoId}`}
                  title={`${recipe.name} 조리 영상`}
                  loading="lazy"
                  allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                  referrerPolicy="strict-origin-when-cross-origin"
                  allowFullScreen
                />
              </div>
              {/* 영상 주인이 퍼가기를 막아 둔 경우를 위해 원본 링크를 함께 둔다 */}
              <p className="field__hint" style={{ marginTop: 8 }}>
                영상이 나오지 않으면 <a href={recipe.youtubeUrl} target="_blank" rel="noreferrer">YouTube에서 보기</a>
              </p>
            </section>
          )}
          <section className="card">
            <h2 className="tile__title">재료</h2>
            <div className="stepper" style={{ margin: '12px 0' }}>
              <button
                type="button"
                aria-label="인분 줄이기"
                disabled={recipe.servings <= 1}
                onClick={() => setServings(recipe.servings - 1)}
              >
                −
              </button>
              <span>{recipe.servings}인분</span>
              <button type="button" aria-label="인분 늘리기" onClick={() => setServings(recipe.servings + 1)}>
                +
              </button>
            </div>
            <ul className="list-plain">
              {recipe.ingredients.map((ingredient) => (
                <li key={ingredient.ingredientId}>
                  {/* 레시피 재료와 상품은 직접 연결되지 않아 재료 이름으로 상품을 찾아 준다 */}
                  <Link to={`/products?keyword=${encodeURIComponent(ingredient.name)}`}>
                    {ingredient.name}
                    {!ingredient.required && ' (선택)'}
                  </Link>
                  <span>{formatAmount(ingredient.requiredAmount, ingredient.unit)}</span>
                </li>
              ))}
            </ul>
            {recipe.ingredients.length === 0 && <p className="field__hint">등록된 재료가 없습니다.</p>}
          </section>

          <section className="card">
            <h2 className="tile__title">조리 순서</h2>
            <ol className="steps-list">
              {recipe.steps.map((step) => (
                <li key={step.order}>{step.description}</li>
              ))}
            </ol>
          </section>
        </div>

        <AutoCartPanel recipe={recipe} member={member} />
      </div>
    </div>
  );
}
