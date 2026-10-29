import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from './ErrorNotice';

// "김치찌개 2인분이랑 계란말이" 같은 문장을 보내면 서버가 레시피를 찾아 자동 장바구니 미리보기를 만든다.
// 결과는 상태 네 가지다: PREVIEWED(바로 미리보기), NEEDS_SELECTION(후보 선택 필요), NO_MATCH, FALLBACK(AI 장애, 대체 목록)
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
          placeholder="예: 김치찌개 2인분이랑 계란말이"
          value={text}
          maxLength={200}
          onChange={(e) => setText(e.target.value)}
          required
        />
        <button type="submit" className="btn" disabled={loading || !isMembership}>
          {loading ? '찾는 중...' : '재료 담기 미리보기'}
        </button>
      </form>
      {!isMembership && (
        <p className="field__hint" style={{ marginTop: 8 }}>
          멤버십 회원만 이용할 수 있습니다.
        </p>
      )}
      <div style={{ marginTop: 12 }}>
        <ErrorNotice error={error} />
        {result?.status === 'NO_MATCH' && <p className="field__hint">맞는 레시피를 찾지 못했습니다. 다른 표현으로 다시 입력해 주세요.</p>}
        {result?.status === 'FALLBACK' && (
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
        {result?.status === 'NEEDS_SELECTION' && (
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
