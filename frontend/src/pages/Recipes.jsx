import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import AiAutoCartBox from '../components/AiAutoCartBox';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import { CUISINE, DIFFICULTY, DISH_TYPE } from '../labels';

const PAGE_SIZE = 12;

export default function Recipes() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = {
    keyword: searchParams.get('keyword') ?? '',
    cuisineType: searchParams.get('cuisineType') ?? '',
    dishType: searchParams.get('dishType') ?? '',
  };
  const page = Number(searchParams.get('page') ?? 0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let ignore = false;
    setError(null);
    api
      .get('/api/recipes', { ...filters, page, size: PAGE_SIZE })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
    // filters는 렌더마다 새 객체라 값으로 비교해야 요청이 반복되지 않는다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.keyword, filters.cuisineType, filters.dishType, page]);

  const update = (next, replace) => {
    const params = {};
    Object.entries(next).forEach(([key, value]) => {
      if (value) params[key] = value;
    });
    setSearchParams(params, { replace });
  };

  // 검색어를 칠 때마다 기록이 쌓이지 않도록 필터 변경은 현재 기록을 덮어쓴다
  const changeFilter = (key) => (event) => update({ ...filters, [key]: event.target.value }, true);
  const setPage = (next) => update({ ...filters, page: next > 0 ? String(next) : '' }, false);

  return (
    <div className="container page">
      <h1 className="page__title">레시피</h1>
      <AiAutoCartBox />
      <div className="filters">
        <input
          className="field__input"
          placeholder="레시피 이름 검색"
          value={filters.keyword}
          onChange={changeFilter('keyword')}
          maxLength={50}
        />
        <select className="field__input" value={filters.cuisineType} onChange={changeFilter('cuisineType')}>
          <option value="">전체 분류</option>
          {Object.entries(CUISINE).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select className="field__input" value={filters.dishType} onChange={changeFilter('dishType')}>
          <option value="">전체 종류</option>
          {Object.entries(DISH_TYPE).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>

      <ErrorNotice error={error} />
      {!result && !error && <p className="state-message">불러오는 중입니다.</p>}
      {result?.content.length === 0 && <p className="state-message">조건에 맞는 레시피가 없습니다.</p>}

      <div className="grid">
        {result?.content.map((recipe) => (
          <Link key={recipe.recipeId} to={`/recipes/${recipe.recipeId}`} className="tile">
            {recipe.thumbnailUrl ? (
              <img className="tile__image" src={recipe.thumbnailUrl} alt="" loading="lazy" />
            ) : (
              <div className="tile__image">이미지 준비 중</div>
            )}
            <div className="tile__body">
              <span className="tile__title">{recipe.name}</span>
              <span className="tile__meta">
                {CUISINE[recipe.cuisineType]} · {DISH_TYPE[recipe.dishType]} · {recipe.baseServings}인분
              </span>
              <span className="tile__meta">
                {recipe.cookingTimeMinutes ? `${recipe.cookingTimeMinutes}분` : ''}{' '}
                {recipe.difficulty ? DIFFICULTY[recipe.difficulty] : ''}
              </span>
            </div>
          </Link>
        ))}
      </div>
      {result && <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </div>
  );
}
