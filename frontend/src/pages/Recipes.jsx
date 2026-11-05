import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import AiAutoCartBox from '../components/AiAutoCartBox';
import BookmarkButton from '../components/BookmarkButton';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import ImagePlaceholder from '../components/ImagePlaceholder';
import Pager from '../components/Pager';
import QuickAdd from '../components/QuickAdd';
import RecentViews from '../components/RecentViews';
import RecipeSearchInput from '../components/RecipeSearchInput';
import { CardSkeletons } from '../components/Skeleton';
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

// 분류·종류는 상품 목록처럼 왼쪽 목록에서 고른다. "전체"를 누르면 그 조건이 풀린다
function SideFilter({ title, value, options, onSelect }) {
  return (
    <section className="side-group">
      <h2 className="side-group__title">{title}</h2>
      <ul className="side-list">
        {[['', '전체'], ...Object.entries(options)].map(([key, text]) => (
          <li key={key || 'all'}>
            <button
              type="button"
              className={`side-list__item${value === key ? ' is-active' : ''}`}
              aria-pressed={value === key}
              onClick={() => onSelect(key)}
            >
              {text}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

// 효과가 정리되면(isCanceled) 남은 쪽을 더 받지 않는다
async function fetchAll(filters, isCanceled) {
  let content = [];
  for (let page = 0; ; page += 1) {
    if (isCanceled()) return content;
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

  // 입력창 값을 주소(searchParams)에서 바로 읽으면 한글 조합 중에 글자가 덮여 밀려 써진다.
  // 그래서 입력값을 따로 들고, 조합이 끝나고 잠깐 멈췄을 때만 주소에 반영한다
  const [keywordInput, setKeywordInput] = useState(filters.keyword);
  const [composing, setComposing] = useState(false);
  const committedKeyword = useRef(filters.keyword);

  // 뒤로 가기 등 외부에서 주소가 바뀌었을 때만 입력창을 맞춘다. 우리가 반영한 값이면 타이핑 중인 글자를 건드리지 않는다
  useEffect(() => {
    if (filters.keyword === committedKeyword.current) return;
    committedKeyword.current = filters.keyword;
    setKeywordInput(filters.keyword);
  }, [filters.keyword]);

  useEffect(() => {
    if (composing || keywordInput === committedKeyword.current) return undefined;
    const timer = setTimeout(() => {
      committedKeyword.current = keywordInput;
      update({ ...current, keyword: keywordInput }, true);
    }, 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keywordInput, composing]);

  useEffect(() => {
    let ignore = false;
    setError(null);
    // 이전 필터의 정렬 결과가 새 결과가 올 때까지(또는 실패해도) 남지 않게 비운다
    if (sort) setAll(null);
    const load = sort
      ? fetchAll(filters, () => ignore).then((list) => !ignore && setAll(list))
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
  const setPage = (next) => update({ ...current, page: next > 0 ? String(next) : '' }, false);
  const cycleSort = (item) => {
    const next =
      sort?.key !== item.key ? { sort: item.key, order: 'asc' } : order === 'asc' ? { sort: item.key, order: 'desc' } : {};
    update({ ...filters, ...next }, true);
  };

  return (
    <div className="container page">
      <h1 className="page__title">레시피</h1>
      <div className="row-actions"><Link to="/recipes/rankings" className="btn btn--outline">레시피 순위</Link><Link to="/auto-carts/new" className="btn btn--outline">여러 메뉴로 장보기</Link></div>
      <AiAutoCartBox />
      <RecentViews type="recipe" title="최근 본 레시피" toPath={(id) => `/recipes/${id}`} />
      <div className="filters">
        <RecipeSearchInput
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onCompositionStart={() => setComposing(true)}
          onCompositionEnd={(e) => {
            setKeywordInput(e.target.value);
            setComposing(false);
          }}
        />
      </div>

      <div className="catalog">
        <aside className="catalog__side" aria-label="레시피 조건">
          <SideFilter
            title="분류"
            value={filters.cuisineType}
            options={CUISINE}
            onSelect={(value) => update({ ...current, cuisineType: value, page: '' }, true)}
          />
          <SideFilter
            title="종류"
            value={filters.dishType}
            options={DISH_TYPE}
            onSelect={(value) => update({ ...current, dishType: value, page: '' }, true)}
          />
        </aside>
        <div className="catalog__main">
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
                      recipe.difficulty ? DIFFICULTY[recipe.difficulty] : null,
                      recipe.spicyLevel != null ? spicyText(recipe.spicyLevel) : null,
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                  {recipe.autoCartAvailable && <span className="badge tile__badge">자동 담기 가능</span>}
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
      </div>
    </div>
  );
}
