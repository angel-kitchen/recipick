import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import AiAutoCartBox from '../components/AiAutoCartBox';
import AutoCartReview from '../components/AutoCartReview';
import BookmarkButton from '../components/BookmarkButton';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import RecentViews from '../components/RecentViews';
import { CardSkeletons, ListSkeleton } from '../components/Skeleton';
import { CUISINE, DIFFICULTY, DISH_TYPE, spicyText } from '../labels';

const PAGE_SIZE = 12;
// 서버는 레시피를 이름순으로만 돌려주고 sort를 받지 않는다. 그래서 정렬을 고르면 조건에 맞는 레시피를 모두 받아 브라우저에서 정렬한다
const FETCH_SIZE = 100;
const DIFFICULTY_RANK = { EASY: 0, NORMAL: 1, HARD: 2 };

// 버튼을 한 번 누르면 asc, 한 번 더 누르면 desc, 또 누르면 기본(이름순)이다. 값이 없는 레시피는 항상 뒤로 보낸다
const SORTS = [
  { key: 'time', label: '조리시간', asc: '짧은순', desc: '긴순', value: (r) => r.cookingTimeMinutes },
  { key: 'spicy', label: '맵기', asc: '순한순', desc: '매운순', value: (r) => r.spicyLevel },
  { key: 'difficulty', label: '난이도', asc: '쉬운순', desc: '어려운순', value: (r) => DIFFICULTY_RANK[r.difficulty] },
];

function sortRecipes(recipes, sort, order) {
  const direction = order === 'desc' ? -1 : 1;
  return [...recipes].sort((a, b) => {
    const x = sort.value(a) ?? null;
    const y = sort.value(b) ?? null;
    if (x === null && y === null) return a.name.localeCompare(b.name, 'ko');
    if (x === null) return 1;
    if (y === null) return -1;
    return (x - y) * direction || a.name.localeCompare(b.name, 'ko');
  });
}

// 분류·종류처럼 고르는 값이 적은 필터는 눌러서 바로 고르게 한다. 같은 칩을 다시 누르면 전체로 돌아간다
function ChipRow({ label, value, options, onSelect }) {
  return (
    <div className="chip-row" role="group" aria-label={label}>
      <span className="chip-row__label">{label}</span>
      <span className="chips">
        {[['', '전체'], ...Object.entries(options)].map(([key, text]) => (
          <button
            key={key}
            type="button"
            className={`chip ${value === key ? 'chip--solid' : ''}`}
            aria-pressed={value === key}
            onClick={() => onSelect(value === key ? '' : key)}
          >
            {text}
          </button>
        ))}
      </span>
    </div>
  );
}

// 상세로 들어가지 않고 카드에서 1인분 재료를 바로 확인해 담는다. 멤버십·배송지·자동 담기 설정이 없으면 이유를 알려 주고 막는다
function QuickAdd({ recipe }) {
  const { member } = useAuth();
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const blockedText = !member.membership?.active
    ? '멤버십 회원만 이용할 수 있어요'
    : !member.mart
      ? '배송지를 등록하면 이용할 수 있어요'
      : !recipe.autoCartAvailable
        ? '아직 자동으로 담을 수 없는 레시피예요'
        : null;

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
      <button type="button" className="btn btn--outline quick-add" disabled={Boolean(blockedText)} title={blockedText ?? undefined} onClick={start}>
        재료 담기
      </button>
      {open && (
        <div className="modal" role="dialog" aria-modal="true" aria-label={`${recipe.name} 재료 담기`}>
          <div className="modal__box modal__box--wide card">
            <h2 className="tile__title">{recipe.name} 1인분 재료</h2>
            <ErrorNotice error={error} />
            {loading && <ListSkeleton rows={4} />}
            {preview && <AutoCartReview preview={preview} />}
            <div className="row-actions" style={{ marginTop: 12 }}>
              <Link to={`/recipes/${recipe.recipeId}`} className="btn btn--outline">
                인분·선택 재료 바꾸기
              </Link>
              <button type="button" className="header__link-button" onClick={() => setOpen(false)}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

async function fetchAll(filters) {
  let content = [];
  for (let page = 0; ; page += 1) {
    const data = await api.get('/api/recipes', { ...filters, page, size: FETCH_SIZE });
    content = content.concat(data.content);
    if (page + 1 >= data.totalPages) return content;
  }
}

export default function Recipes() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = {
    keyword: searchParams.get('keyword') ?? '',
    cuisineType: searchParams.get('cuisineType') ?? '',
    dishType: searchParams.get('dishType') ?? '',
  };
  const page = Number(searchParams.get('page') ?? 0);
  const sort = SORTS.find((item) => item.key === searchParams.get('sort')) ?? null;
  const order = searchParams.get('order') === 'desc' ? 'desc' : 'asc';
  // 서버가 나눠 준 한 쪽(기본)과, 정렬할 때 모두 받아 온 목록
  const [result, setResult] = useState(null);
  const [all, setAll] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    setError(null);
    const load = sort
      ? fetchAll(filters).then((list) => !ignore && setAll(list))
      : api.get('/api/recipes', { ...filters, page, size: PAGE_SIZE }).then((data) => !ignore && setResult(data));
    load.catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
    // filters는 렌더마다 새 객체라 값으로 비교해야 요청이 반복되지 않는다. 정렬 중에는 쪽을 넘겨도 다시 받지 않는다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.keyword, filters.cuisineType, filters.dishType, sort ? 'all' : page]);

  // 정렬 중에는 받아 온 전체를 정렬해 이 쪽 몫만 잘라 보여 준다
  const view = useMemo(() => {
    if (!sort) return result;
    if (!all) return null;
    const sorted = sortRecipes(all, sort, order);
    const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
    const current = Math.min(page, Math.max(totalPages - 1, 0));
    return { content: sorted.slice(current * PAGE_SIZE, (current + 1) * PAGE_SIZE), page: current, totalPages };
  }, [sort, order, all, result, page]);

  const setBookmarked = (recipeId, bookmarked) => {
    const mark = (list) => list.map((recipe) => (recipe.recipeId === recipeId ? { ...recipe, bookmarked } : recipe));
    setResult((prev) => prev && { ...prev, content: mark(prev.content) });
    setAll((prev) => prev && mark(prev));
  };

  const update = (next, replace) => {
    const params = {};
    Object.entries(next).forEach(([key, value]) => {
      if (value) params[key] = value;
    });
    setSearchParams(params, { replace });
  };

  // 검색어를 칠 때마다 기록이 쌓이지 않도록 필터 변경은 현재 기록을 덮어쓴다
  const current = { ...filters, sort: sort?.key, order: sort ? order : undefined };
  const changeFilter = (key) => (event) => update({ ...current, [key]: event.target.value }, true);
  const setPage = (next) => update({ ...current, page: next > 0 ? String(next) : '' }, false);
  const cycleSort = (item) => {
    const next =
      sort?.key !== item.key ? { sort: item.key, order: 'asc' } : order === 'asc' ? { sort: item.key, order: 'desc' } : {};
    update({ ...filters, ...next }, true);
  };

  return (
    <div className="container page">
      <h1 className="page__title">레시피</h1>
      <AiAutoCartBox />
      <RecentViews type="recipe" title="최근 본 레시피" toPath={(id) => `/recipes/${id}`} />
      <div className="filters">
        <input
          className="field__input"
          placeholder="레시피 이름 검색"
          value={filters.keyword}
          onChange={changeFilter('keyword')}
          maxLength={50}
        />
      </div>
      <ChipRow label="분류" value={filters.cuisineType} options={CUISINE} onSelect={(value) => update({ ...current, cuisineType: value, page: '' }, true)} />
      <ChipRow label="종류" value={filters.dishType} options={DISH_TYPE} onSelect={(value) => update({ ...current, dishType: value, page: '' }, true)} />

      <div className="sort-bar" role="group" aria-label="정렬">
        <button type="button" className={`sort-bar__item ${sort ? '' : 'is-on'}`} aria-pressed={!sort} onClick={() => update(filters, true)}>
          {sort ? '' : '✓ '}이름순
        </button>
        {SORTS.map((item) => {
          const on = sort?.key === item.key;
          return (
            <button key={item.key} type="button" className={`sort-bar__item ${on ? 'is-on' : ''}`} aria-pressed={on} onClick={() => cycleSort(item)}>
              {on ? `✓ ${item.label} ${order === 'asc' ? item.asc : item.desc}` : `${item.label}순`}
            </button>
          );
        })}
      </div>

      <ErrorNotice error={error} />
      {!view && !error && <CardSkeletons />}
      {view?.content.length === 0 && (
        <EmptyState icon="recipe" title="조건에 맞는 레시피가 없어요" hint="검색어나 분류를 바꿔 보세요." actions={[{ to: '/recipes', label: '조건 지우기' }]} />
      )}

      <div className="grid">
        {view?.content.map((recipe) => (
          <div key={recipe.recipeId} className="tile-wrap tile-wrap--quick">
          <Link to={`/recipes/${recipe.recipeId}`} className="tile">
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
                  recipe.difficulty ? DIFFICULTY[recipe.difficulty] : null,
                  recipe.spicyLevel != null ? spicyText(recipe.spicyLevel) : null,
                ]
                  .filter(Boolean)
                  .join(' · ')}
              </span>
            </div>
          </Link>
          <BookmarkButton
            recipeId={recipe.recipeId}
            bookmarked={recipe.bookmarked}
            onChange={(next) => setBookmarked(recipe.recipeId, next)}
            onError={setError}
          />
          <QuickAdd recipe={recipe} />
          </div>
        ))}
      </div>
      {view && <Pager page={view.page} totalPages={view.totalPages} onChange={setPage} />}
    </div>
  );
}
