import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { CUISINE, DISH_TYPE, spicyText } from '../labels';
import ErrorNotice from './ErrorNotice';

// 한 입력창으로 두 가지를 처리한다.
// ① "김치찌개 2인분이랑 계란말이"처럼 메뉴 이름이 있으면 서버가 레시피를 찾아 자동 장바구니 미리보기를 만든다.
//    결과는 PREVIEWED(바로 미리보기), NEEDS_SELECTION(후보 선택 필요), NO_MATCH, FALLBACK(AI 장애, 대체 목록)
// ② 이름으로 못 찾으면(NO_MATCH) 같은 문장을 "매운음식 30분 이내" 같은 조건으로 보고 레시피를 추천받는다.
//    결과는 RECOMMENDED, NO_MATCH, FALLBACK이고, 결과에 source: 'recommend'를 붙여 ①과 구분한다.
// ①이 AI 장애(FALLBACK)로 끝났을 때는 ②를 부르지 않는다. 같은 AI가 다시 실패할 가능성이 크고 호출 횟수만 쓴다.
export default function AiAutoCartBox() {
  const navigate = useNavigate();
  const { member } = useAuth();
  const isMembership = Boolean(member?.membership?.active);
  const [text, setText] = useState('');
  const [result, setResult] = useState(null);
  const [picked, setPicked] = useState({});
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const run = async (action) => {
    setError(null);
    setLoading(true);
    try {
      await action();
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };

  const submit = (event) => {
    event.preventDefault();
    run(async () => {
      const data = await api.post('/api/auto-carts/ai-preview', { text });
      if (data.status === 'PREVIEWED') {
        navigate(`/auto-carts/${data.preview.autoCartRequestId}`);
        return;
      }
      setPicked({});
      if (data.status === 'NO_MATCH') {
        const recommended = await api.post('/api/recipes/ai-recommendations', { text });
        setResult({ ...recommended, source: 'recommend' });
        return;
      }
      setResult(data);
    });
  };

  const confirmSelection = () =>
    run(async () => {
      const menus = [];
      for (const [index, menu] of result.menus.entries()) {
        const recipeId = Number(picked[index] ?? menu.candidates[0]?.recipeId);
        if (!recipeId) continue;
        // 인분이 없으면 레시피 기본 인분을 쓰므로 상세에서 읽어 온다
        const servings = menu.servings ?? (await api.get(`/api/recipes/${recipeId}`)).baseServings;
        menus.push({ recipeId, servings, optionalIngredientIds: [] });
      }
      if (menus.length === 0) {
        setResult({ status: 'NO_MATCH' });
        return;
      }
      const preview = await api.post('/api/auto-carts/preview', { menus });
      navigate(`/auto-carts/${preview.autoCartRequestId}`);
    });

  return (
    <section className="card" style={{ marginBottom: 24 }}>
      <h2 className="tile__title">먹고 싶은 걸 말해보세요</h2>
      <form className="filters" style={{ marginTop: 12, marginBottom: 0 }} onSubmit={submit}>
        <input
          className="field__input"
          style={{ flex: 1 }}
          placeholder="예: 김치찌개 2인분이랑 계란말이 / 매운 음식 30분 이내"
          value={text}
          maxLength={200}
          onChange={(e) => setText(e.target.value)}
          required
        />
        <button type="submit" className="btn" disabled={loading || !isMembership}>
          {loading ? '찾는 중...' : '찾아보기'}
        </button>
      </form>
      {!isMembership && (
        <p className="field__hint" style={{ marginTop: 8 }}>
          멤버십 회원만 이용할 수 있습니다.
        </p>
      )}
      <div style={{ marginTop: 12 }}>
        <ErrorNotice error={error} />
        {result?.source === 'recommend' && <Recommendations result={result} />}
        {result?.source !== 'recommend' && result?.status === 'NO_MATCH' && (
          <p className="field__hint">맞는 레시피를 찾지 못했습니다. 다른 표현으로 다시 입력해 주세요.</p>
        )}
        {result?.source !== 'recommend' && result?.status === 'FALLBACK' && (
          <>
            <p className="field__hint">지금은 문장을 이해하지 못해 대신 레시피를 골라 볼 수 있게 보여드립니다.</p>
            <div className="chips">
              {result.recipes.map((recipe) => (
                <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="chip">
                  {recipe.name}
                </Link>
              ))}
            </div>
          </>
        )}
        {result?.source !== 'recommend' && result?.status === 'NEEDS_SELECTION' && (
          <div className="stack">
            <p className="field__hint">어떤 레시피인지 골라 주세요.</p>
            {result.menus.map((menu, index) => (
              <label key={menu.mention} className="field">
                <span className="field__label">
                  {menu.mention}
                  {menu.servings ? ` (${menu.servings}인분)` : ''}
                </span>
                {menu.candidates.length === 0 ? (
                  <span className="error-text">맞는 레시피가 없습니다.</span>
                ) : (
                  <select
                    className="field__input"
                    value={picked[index] ?? menu.candidates[0].recipeId}
                    onChange={(e) => setPicked((prev) => ({ ...prev, [index]: e.target.value }))}
                  >
                    {menu.candidates.map((candidate) => (
                      <option key={candidate.recipeId} value={candidate.recipeId}>
                        {candidate.name}
                      </option>
                    ))}
                  </select>
                )}
              </label>
            ))}
            <div>
              <button type="button" className="btn" disabled={loading} onClick={confirmSelection}>
                선택한 레시피로 미리보기
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

// 조건 추천 결과. reason은 AI가 쓴 이유라 AI 없이 후보만 보여 준 FALLBACK에서는 없다
function Recommendations({ result }) {
  if (result.status === 'NO_MATCH') {
    return <p className="field__hint">조건에 맞는 레시피를 찾지 못했습니다. 조건을 줄여서 다시 입력해 주세요.</p>;
  }
  if (result.status === 'FALLBACK' && result.recipes.length === 0) {
    return <p className="field__hint">지금은 문장을 이해하지 못했습니다. 아래 목록의 필터로 직접 찾아 주세요.</p>;
  }
  return (
    <>
      <p className="field__hint">
        {result.status === 'FALLBACK'
          ? '지금은 AI가 고르지 못해 조건에 맞는 레시피를 순서대로 보여드립니다.'
          : '말씀하신 조건으로 골라 봤어요.'}
      </p>
      <div className="grid" style={{ marginTop: 12 }}>
        {result.recipes.map((recipe) => (
          <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="tile">
            {recipe.thumbnailUrl ? (
              <img className="tile__image" src={recipe.thumbnailUrl} alt="" loading="lazy" />
            ) : (
              <div className="tile__image">이미지 준비 중</div>
            )}
            <div className="tile__body">
              <span className="tile__title">{recipe.name}</span>
              <span className="tile__meta">
                {CUISINE[recipe.cuisineType]} · {DISH_TYPE[recipe.dishType]}
              </span>
              <span className="tile__meta">
                {[
                  recipe.cookingTimeMinutes ? `${recipe.cookingTimeMinutes}분` : null,
                  recipe.spicyLevel != null ? spicyText(recipe.spicyLevel) : null,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
              {recipe.reason && <span className="tile__meta">{recipe.reason}</span>}
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}
