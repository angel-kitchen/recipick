import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { CUISINE, DIFFICULTY, DISH_TYPE, RECIPE_STATUS, SALE_UNIT } from '../../labels';
import useLoad from '../useLoad';

const EMPTY = {
  name: '',
  cuisineType: 'KOREAN',
  dishType: 'STEW',
  baseServings: '2',
  cookingTimeMinutes: '30',
  difficulty: 'EASY',
  spicyLevel: '0',
  youtubeUrl: '',
  steps: '',
};

// 조리 순서는 한 줄에 한 단계로 입력받는다
function RecipeForm({ recipeId, onDone, onCancel }) {
  const editing = Boolean(recipeId);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const change = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  useEffect(() => {
    if (!editing) return;
    api
      .get(`/api/admin/recipes/${recipeId}`)
      .then((recipe) =>
        setForm({
          name: recipe.name,
          cuisineType: recipe.cuisineType,
          dishType: recipe.dishType,
          baseServings: String(recipe.baseServings),
          cookingTimeMinutes: String(recipe.cookingTimeMinutes ?? ''),
          difficulty: recipe.difficulty ?? 'EASY',
          spicyLevel: String(recipe.spicyLevel ?? 0),
          youtubeUrl: recipe.youtubeUrl ?? '',
          steps: recipe.steps.map((step) => step.description).join('\n'),
        }),
      )
      .catch(setError);
  }, [editing, recipeId]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    const body = {
      name: form.name,
      cuisineType: form.cuisineType,
      dishType: form.dishType,
      baseServings: Number(form.baseServings),
      cookingTimeMinutes: Number(form.cookingTimeMinutes),
      difficulty: form.difficulty,
      spicyLevel: Number(form.spicyLevel),
      youtubeUrl: form.youtubeUrl || null,
      steps: form.steps
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean)
        .map((description, index) => ({ order: index + 1, description })),
    };
    try {
      if (editing) await api.patch(`/api/admin/recipes/${recipeId}`, body);
      else await api.post('/api/admin/recipes', body);
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">{editing ? '레시피 수정' : '레시피 등록'}</h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        <label className="field">
          <span className="field__label">이름</span>
          <input className="field__input" value={form.name} onChange={change('name')} maxLength={50} required />
        </label>
        <label className="field">
          <span className="field__label">나라별 분류</span>
          <select className="field__input" value={form.cuisineType} onChange={change('cuisineType')}>
            {Object.entries(CUISINE).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">요리 종류</span>
          <select className="field__input" value={form.dishType} onChange={change('dishType')}>
            {Object.entries(DISH_TYPE).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">기본 인분</span>
          <input className="field__input" inputMode="numeric" value={form.baseServings} onChange={change('baseServings')} required />
        </label>
        <label className="field">
          <span className="field__label">조리 시간(분)</span>
          <input className="field__input" inputMode="numeric" value={form.cookingTimeMinutes} onChange={change('cookingTimeMinutes')} required />
        </label>
        <label className="field">
          <span className="field__label">난이도</span>
          <select className="field__input" value={form.difficulty} onChange={change('difficulty')}>
            {Object.entries(DIFFICULTY).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">매운 정도 (0~3)</span>
          <input className="field__input" inputMode="numeric" value={form.spicyLevel} onChange={change('spicyLevel')} required />
        </label>
        <label className="field">
          <span className="field__label">유튜브 주소</span>
          <input className="field__input" value={form.youtubeUrl} onChange={change('youtubeUrl')} />
        </label>
      </div>
      <label className="field">
        <span className="field__label">조리 순서 (한 줄에 한 단계)</span>
        <textarea className="field__input" rows={6} value={form.steps} onChange={change('steps')} required />
      </label>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn" disabled={saving}>
          {saving ? '저장 중...' : '저장'}
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  );
}

function IngredientsEditor({ recipeId, onDone, onCancel }) {
  const [rows, setRows] = useState(null);
  const [options, setOptions] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([api.get(`/api/admin/recipes/${recipeId}`), api.get('/api/admin/ingredients', { size: 100 })])
      .then(([recipe, ingredients]) => {
        setOptions(ingredients.content);
        setRows(
          recipe.ingredients.map((item) => ({
            ingredientId: String(item.ingredientId),
            amountPerServing: String(item.amountPerServing),
            required: item.required,
            linkedProductCount: item.linkedProductCount,
          })),
        );
      })
      .catch(setError);
  }, [recipeId]);

  const update = (index, patch) => setRows((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const save = async () => {
    setError(null);
    try {
      await api.put(`/api/admin/recipes/${recipeId}/ingredients`, {
        ingredients: rows.map((row) => ({
          ingredientId: Number(row.ingredientId),
          amountPerServing: row.amountPerServing,
          required: row.required,
        })),
      });
      onDone();
    } catch (e) {
      setError(e);
    }
  };

  if (!rows) return <ErrorNotice error={error} />;

  return (
    <section className="card admin__section">
      <h2 className="tile__title">재료 연결 (1인분 기준)</h2>
      <ErrorNotice error={error} />
      <div className="stack" style={{ margin: '12px 0' }}>
        {rows.map((row, index) => (
          <div key={index} className="filters" style={{ marginBottom: 0 }}>
            <select
              className="field__input"
              value={row.ingredientId}
              onChange={(e) => update(index, { ingredientId: e.target.value })}
            >
              <option value="">재료 선택</option>
              {options.map((option) => (
                <option key={option.ingredientId} value={option.ingredientId}>
                  {option.name} ({SALE_UNIT[option.unit]})
                </option>
              ))}
            </select>
            <input
              className="field__input"
              style={{ width: 100 }}
              inputMode="decimal"
              placeholder="수량"
              value={row.amountPerServing}
              onChange={(e) => update(index, { amountPerServing: e.target.value })}
            />
            <label className="check">
              <input type="checkbox" checked={row.required} onChange={(e) => update(index, { required: e.target.checked })} />
              필수
            </label>
            {row.linkedProductCount !== undefined && (
              <span className={row.linkedProductCount === 0 ? 'error-text' : 'field__hint'}>
                {row.linkedProductCount === 0 ? '연결 상품 없음 · 상품에 이 재료를 연결해 주세요' : `연결 상품 ${row.linkedProductCount}개`}
              </span>
            )}
            <button type="button" className="chip" onClick={() => setRows((prev) => prev.filter((_, i) => i !== index))}>
              삭제
            </button>
          </div>
        ))}
      </div>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button
          type="button"
          className="btn btn--outline"
          onClick={() => setRows((prev) => [...prev, { ingredientId: '', amountPerServing: '', required: true }])}
        >
          재료 추가
        </button>
        <button type="button" className="btn" onClick={save}>
          저장
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          취소
        </button>
      </div>
    </section>
  );
}

export default function AdminRecipes() {
  const [filter, setFilter] = useState({ keyword: '', cuisineType: '', autoCartAvailable: '', status: '' });
  const [page, setPage] = useState(0);
  const [panel, setPanel] = useState(null);
  const [actionError, setActionError] = useState(null);
  const { data, error, reload } = useLoad(() => api.get('/api/admin/recipes', { ...filter, page, size: 20 }), [filter, page]);

  const setFilterValue = (key) => (e) => {
    setPage(0);
    setFilter((prev) => ({ ...prev, [key]: e.target.value }));
  };

  const act = async (action) => {
    setActionError(null);
    try {
      await action();
      reload();
    } catch (e) {
      setActionError(e);
    }
  };

  const closeAndReload = () => {
    setPanel(null);
    reload();
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">레시피</h1>
        <button type="button" className="btn" onClick={() => setPanel({ type: 'form' })}>
          레시피 등록
        </button>
      </div>
      {panel?.type === 'form' && (
        <RecipeForm key={panel.recipeId ?? 'new'} recipeId={panel.recipeId} onDone={closeAndReload} onCancel={() => setPanel(null)} />
      )}
      {panel?.type === 'ingredients' && (
        <IngredientsEditor key={panel.recipeId} recipeId={panel.recipeId} onDone={closeAndReload} onCancel={() => setPanel(null)} />
      )}
      <div className="filters">
        <input className="field__input" placeholder="이름 검색" value={filter.keyword} onChange={setFilterValue('keyword')} />
        <select className="field__input" value={filter.cuisineType} onChange={setFilterValue('cuisineType')}>
          <option value="">분류 전체</option>
          {Object.entries(CUISINE).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select className="field__input" value={filter.autoCartAvailable} onChange={setFilterValue('autoCartAvailable')}>
          <option value="">자동 담기 전체</option>
          <option value="true">켜짐</option>
          <option value="false">꺼짐</option>
        </select>
        <select className="field__input" value={filter.status} onChange={setFilterValue('status')}>
          <option value="">공개 상태 전체</option>
          {Object.entries(RECIPE_STATUS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <ErrorNotice error={error ?? actionError} />
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이름</th>
              <th>분류</th>
              <th>종류</th>
              <th>공개</th>
              <th>자동 담기</th>
              <th>상품 미연결 필수 재료</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((recipe) => (
              <tr key={recipe.recipeId}>
                <td>{recipe.recipeId}</td>
                <td>{recipe.name}</td>
                <td>{CUISINE[recipe.cuisineType]}</td>
                <td>{DISH_TYPE[recipe.dishType]}</td>
                <td>{RECIPE_STATUS[recipe.status]}</td>
                <td>{recipe.autoCartAvailable ? '켜짐' : '꺼짐'}</td>
                <td>{recipe.missingLinkedRequiredCount}</td>
                <td>
                  <span className="chips">
                    <button type="button" className="chip" onClick={() => setPanel({ type: 'form', recipeId: recipe.recipeId })}>
                      수정
                    </button>
                    <button type="button" className="chip" onClick={() => setPanel({ type: 'ingredients', recipeId: recipe.recipeId })}>
                      재료
                    </button>
                    <button
                      type="button"
                      className="chip"
                      onClick={() =>
                        act(() =>
                          api.patch(`/api/admin/recipes/${recipe.recipeId}/auto-cart`, {
                            autoCartAvailable: !recipe.autoCartAvailable,
                          }),
                        )
                      }
                    >
                      자동 담기 {recipe.autoCartAvailable ? '끄기' : '켜기'}
                    </button>
                    <button
                      type="button"
                      className="chip"
                      onClick={() =>
                        act(() =>
                          api.patch(`/api/admin/recipes/${recipe.recipeId}/status`, {
                            status: recipe.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
                          }),
                        )
                      }
                    >
                      {recipe.status === 'ACTIVE' ? '비공개로' : '공개로'}
                    </button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">레시피가 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
