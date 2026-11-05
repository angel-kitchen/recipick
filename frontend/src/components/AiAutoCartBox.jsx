import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { readMemory, writeMemory } from '../chatMemory';
import { CUISINE, DISH_TYPE, formatWon, spicyText } from '../labels';
import ErrorNotice from './ErrorNotice';
import ImagePlaceholder from './ImagePlaceholder';
import MembershipInfoModal from './MembershipInfoModal';
import RecipeChat from './RecipeChat';
import RepiSearching from './RepiSearching';

// 메뉴·조건과 가진 재료는 같은 문장이 양쪽으로 읽힐 수 있어("두부랑 김치") 자동으로 나누지 않고 탭으로 고르게 한다
const MODES = {
  menu: {
    label: '메뉴·조건으로 찾기',
    placeholder: '예: 김치찌개 2인분이랑 계란말이 / 매운 음식 30분 이내',
  },
  fridge: {
    label: '가진 재료로 찾기',
    placeholder: '예: 두부랑 김치 있는데 뭐 해먹지?',
  },
  mealPlan: {
    label: '식단 짜기',
    placeholder: '예: 이번 주 저녁 5일치, 겹치지 않게',
  },
};

// 한 입력창으로 두 가지를 처리한다.
// ① "김치찌개 2인분이랑 계란말이"처럼 메뉴 이름이 있으면 서버가 레시피를 찾아 자동 장바구니 미리보기를 만든다.
//    결과는 PREVIEWED(바로 미리보기), NEEDS_SELECTION(후보 선택 필요), NO_MATCH, FALLBACK(AI 장애, 대체 목록)
// ② 이름으로 못 찾으면(NO_MATCH) 같은 문장을 "매운음식 30분 이내" 같은 조건으로 보고 레시피를 추천받는다.
//    결과는 RECOMMENDED, NO_MATCH, FALLBACK이고, 결과에 source: 'recommend'를 붙여 ①과 구분한다.
// 가진 재료 탭은 냉장고 파먹기만 부르고, 결과에 source: 'fridge'를 붙인다.
// 식단 짜기 탭은 일주일 식단만 부르고, 결과에 source: 'mealPlan'을 붙인다.
// ①이 AI 장애(FALLBACK)로 끝났을 때는 ②를 부르지 않는다. 같은 AI가 다시 실패할 가능성이 크고 호출 횟수만 쓴다.
export default function AiAutoCartBox() {
  const navigate = useNavigate();
  const { member } = useAuth();
  const isMembership = Boolean(member?.membership?.active);
  // 상세 화면에 갔다가 뒤로 오면 입력과 결과를 그대로 보여 줘서, AI를 다시 부르지 않게 한다(chatMemory.js)
  const [saved] = useState(() => readMemory('detail', member?.memberId));
  const [mode, setMode] = useState(saved?.mode ?? 'menu');
  const [text, setText] = useState(saved?.text ?? '');
  const [result, setResult] = useState(saved?.result ?? null);
  const [picked, setPicked] = useState(saved?.picked ?? {});
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [martMissing, setMartMissing] = useState(saved?.martMissing ?? false);
  const [membershipInfo, setMembershipInfo] = useState(false);
  // 채팅형 도우미가 기본이고, 이 세 가지 검색은 "자세히 찾기"를 누르면 같은 상자가 길어지며 보인다. 닫아도 입력·결과는 남긴다
  const [detail, setDetail] = useState(saved?.detail ?? false);

  useEffect(() => {
    if (loading || error || !member) return;
    writeMemory('detail', member.memberId, { detail, mode, text, result, picked, martMissing });
  }, [loading, error, member, detail, mode, text, result, picked, martMissing]);

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

  const changeMode = (next) => {
    setMode(next);
    setResult(null);
    setError(null);
    setMartMissing(false);
  };

  const submit = (event) => {
    event.preventDefault();
    // 새 검색이 실패해도 이전 검색의 선택 화면이 남아 있지 않게 먼저 비운다
    setResult(null);
    setMartMissing(false);
    run(async () => {
      if (mode === 'fridge') {
        const answered = await api.post('/api/recipes/ai-fridge-recommendations', { text });
        setResult({ ...answered, source: 'fridge' });
        return;
      }
      if (mode === 'mealPlan') {
        try {
          const planned = await api.post('/api/auto-carts/ai-meal-plans', { text });
          setResult({ ...planned, source: 'mealPlan' });
        } catch (e) {
          // 식단은 장바구니 미리보기를 만드는 기능이라 이용 마트가 없으면 대신 보여 줄 결과가 없다
          if (e.code !== 'ORDER-004') throw e;
          setMartMissing(true);
        }
        return;
      }
      let data;
      try {
        data = await api.post('/api/auto-carts/ai-preview', { text });
      } catch (e) {
        // 이용 마트가 없으면 메뉴 장보기는 못 하지만 조건 추천은 마트 없이도 된다
        if (e.code !== 'ORDER-004') throw e;
        setMartMissing(true);
        data = { status: 'NO_MATCH' };
      }
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
        const servings = menu.servings ?? (await api.get(`/api/recipes/${recipeId}/metadata`)).baseServings;
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
    <RecipeChat detailOpen={detail} onToggleDetail={() => setDetail((open) => !open)}>
      <div className="chips">
        {Object.entries(MODES).map(([value, { label }]) => (
          <button
            key={value}
            type="button"
            className={`chip ${mode === value ? 'chip--solid' : ''}`}
            onClick={() => changeMode(value)}
          >
            {label}
          </button>
        ))}
      </div>
      <form className="filters" style={{ marginTop: 12, marginBottom: 0 }} onSubmit={submit}>
        <input
          className="field__input"
          style={{ flex: 1 }}
          placeholder={MODES[mode].placeholder}
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
        <p className="field__hint auth-card__footer" style={{ marginTop: 8, textAlign: 'left' }}>
          AI 검색은 멤버십 회원만 쓸 수 있어요.{' '}
          <button type="button" onClick={() => setMembershipInfo(true)}>
            멤버십 알아보기
          </button>
        </p>
      )}
      {membershipInfo && <MembershipInfoModal onClose={() => setMembershipInfo(false)} />}
      <div style={{ marginTop: 12 }}>
        {loading && <RepiSearching />}
        <ErrorNotice error={error} />
        {martMissing && (
          <p className="field__hint">
            {mode === 'mealPlan'
              ? '이용 마트가 있어야 식단 재료를 장바구니 미리보기로 만들 수 있어요.'
              : '이용 마트가 없어 재료 담기 미리보기는 쓸 수 없어요. 조건에 맞는 레시피만 보여 드려요.'}{' '}
            <Link to="/addresses">배송지 등록하기</Link>
          </p>
        )}
        {result?.source === 'recommend' && <Recommendations result={result} />}
        {result?.source === 'fridge' && <FridgeAnswer result={result} />}
        {result?.source === 'mealPlan' && <MealPlan result={result} onOpen={(id) => navigate(`/auto-carts/${id}`)} />}
        {!result?.source && result?.status === 'NO_MATCH' && (
          <p className="ai-lead">맞는 레시피를 찾지 못했어요. 다른 표현으로 다시 입력해 주세요.</p>
        )}
        {!result?.source && result?.status === 'FALLBACK' && (
          <>
            <p className="ai-lead">잘 알아듣지 못했어요. 대신 이런 레시피는 어때요?</p>
            <div className="chips">
              {result.recipes.map((recipe) => (
                <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="chip">
                  {recipe.name}
                </Link>
              ))}
            </div>
          </>
        )}
        {!result?.source && result?.status === 'NEEDS_SELECTION' && (
          <div className="stack">
            <p className="ai-lead">어떤 레시피인지 골라 주세요.</p>
            {result.menus.map((menu, index) => (
              <label key={menu.mention} className="field">
                <span className="field__label">
                  {menu.mention}
                  {menu.servings ? ` (${menu.servings}인분)` : ''}
                </span>
                {menu.candidates.length === 0 ? (
                  <span className="error-text">맞는 레시피가 없어요.</span>
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
    </RecipeChat>
  );
}

// 조건 추천 결과. reason은 AI가 쓴 이유라 AI 없이 후보만 보여 준 FALLBACK에서는 없다
function Recommendations({ result }) {
  if (result.status === 'NO_MATCH') {
    return <p className="ai-lead">조건에 맞는 레시피를 찾지 못했어요. 조건을 줄여서 다시 입력해 주세요.</p>;
  }
  if (result.status === 'FALLBACK' && result.recipes.length === 0) {
    return <p className="ai-lead">잘 알아듣지 못했어요. 아래 필터로 직접 찾아볼까요?</p>;
  }
  return (
    <>
      <p className="ai-lead">
        {result.status === 'FALLBACK'
          ? 'AI가 고르지 못해서 조건에 맞는 레시피를 순서대로 보여 드려요.'
          : '이렇게 골라 봤어요'}
      </p>
      <div className="grid" style={{ marginTop: 12 }}>
        {result.recipes.map((recipe) => (
          <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="tile">
            {recipe.thumbnailUrl ? (
              <img className="tile__image" src={recipe.thumbnailUrl} alt="" loading="lazy" />
            ) : (
              <ImagePlaceholder />
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
              {recipe.reason && <span className="ai-reason">{recipe.reason}</span>}
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}

// 이 응답에는 사진 주소가 없어서, 레시피 이름으로 목록을 검색해 같은 번호의 사진을 가져온다(AI 호출 아님).
// 사진은 덤이라 못 가져와도 접시 그림으로 두고 넘어간다
const thumbnailCache = new Map();

function useThumbnails(recipes) {
  const [urls, setUrls] = useState({});
  useEffect(() => {
    let ignore = false;
    Promise.all(
      recipes.map(async ({ recipeId, name }) => {
        if (!thumbnailCache.has(recipeId)) {
          const data = await api.get('/api/recipes', { keyword: name, size: 20 });
          thumbnailCache.set(recipeId, data.content.find((item) => item.recipeId === recipeId)?.thumbnailUrl ?? null);
        }
        return [recipeId, thumbnailCache.get(recipeId)];
      }),
    )
      .then((pairs) => !ignore && setUrls(Object.fromEntries(pairs)))
      .catch(() => {});
    return () => {
      ignore = true;
    };
  }, [recipes]);
  return urls;
}

// 냉장고 파먹기 결과. 어떤 재료가 부족한지는 보여 주지 않고, 골라 준 레시피와 가진 재료만 짧게 보여 준다.
// 레시피가 없을 때(NO_MATCH)만 AI가 쓴 안내(answer)를 그대로 쓴다
function FridgeAnswer({ result }) {
  const thumbnails = useThumbnails(result.recipes);
  if (result.status === 'FALLBACK') {
    return <p className="ai-lead">지금은 답변을 드리기 어려워요. 아래 검색으로 레시피를 찾아 주세요.</p>;
  }
  return (
    <>
      <p className="ai-lead">{result.recipes.length > 0 ? '이렇게 골라 봤어요' : result.answer}</p>
      <div className="grid" style={{ marginTop: 12 }}>
        {result.recipes.map((recipe) => (
          <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="tile">
            {thumbnails[recipe.recipeId] ? (
              <img className="tile__image" src={thumbnails[recipe.recipeId]} alt="" loading="lazy" />
            ) : (
              <ImagePlaceholder />
            )}
            <div className="tile__body">
              <span className="tile__title">{recipe.name}</span>
              <span className="ai-reason">가진 재료: {recipe.ownedIngredients.join(', ')}</span>
            </div>
          </Link>
        ))}
      </div>
    </>
  );
}

// 일주일 식단 결과. 고른 이유는 미리보기 화면에 없어서, 바로 넘기지 않고 한 번 보여 준 뒤 버튼으로 이동한다
function MealPlan({ result, onOpen }) {
  if (result.status === 'NO_MATCH') {
    return <p className="ai-lead">식단을 짜지 못했어요. "이번 주 저녁 3일치"처럼 다시 입력해 주세요.</p>;
  }
  if (result.status === 'FALLBACK') {
    return <p className="ai-lead">지금은 식단을 짜기 어려워요. 아래 검색으로 레시피를 찾아 주세요.</p>;
  }
  return (
    <>
      <p className="ai-lead">
        {result.meals.length}가지로 식단을 짰어요. 재료 예상 금액 {formatWon(result.preview.totalPrice)}
      </p>
      <div className="grid" style={{ marginTop: 12 }}>
        {result.meals.map((meal) => (
          <Link key={meal.recipeId} to={`/recipes/${meal.recipeId}`} className="tile">
            {meal.thumbnailUrl ? (
              <img className="tile__image" src={meal.thumbnailUrl} alt="" loading="lazy" />
            ) : (
              <ImagePlaceholder />
            )}
            <div className="tile__body">
              <span className="tile__title">{meal.name}</span>
              <span className="tile__meta">{meal.servings}인분</span>
              {meal.reason && <span className="ai-reason">{meal.reason}</span>}
            </div>
          </Link>
        ))}
      </div>
      <div className="row-actions">
        <button type="button" className="btn" onClick={() => onOpen(result.preview.autoCartRequestId)}>
          장바구니 미리보기
        </button>
      </div>
    </>
  );
}
