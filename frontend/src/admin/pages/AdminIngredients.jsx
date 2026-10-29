import { useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { SALE_UNIT } from '../../labels';
import useLoad from '../useLoad';

function IngredientForm({ ingredient, onDone, onCancel }) {
  const editing = Boolean(ingredient);
  const [form, setForm] = useState({ name: ingredient?.name ?? '', unit: ingredient?.unit ?? 'G' });
  const [error, setError] = useState(null);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      if (editing) await api.patch(`/api/admin/ingredients/${ingredient.ingredientId}`, form);
      else await api.post('/api/admin/ingredients', form);
      onDone();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">{editing ? '재료 수정' : '재료 등록'}</h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        <label className="field">
          <span className="field__label">이름</span>
          <input
            className="field__input"
            value={form.name}
            maxLength={50}
            onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            required
          />
        </label>
        <label className="field">
          <span className="field__label">단위</span>
          <select className="field__input" value={form.unit} onChange={(e) => setForm((prev) => ({ ...prev, unit: e.target.value }))}>
            {Object.entries(SALE_UNIT).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn">
          저장
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  );
}

export default function AdminIngredients() {
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [panel, setPanel] = useState(null);
  const { data, error, reload } = useLoad(() => api.get('/api/admin/ingredients', { keyword, page, size: 20 }), [keyword, page]);

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">재료</h1>
        <button type="button" className="btn" onClick={() => setPanel({})}>
          재료 등록
        </button>
      </div>
      {panel && (
        <IngredientForm
          key={panel.ingredient?.ingredientId ?? 'new'}
          ingredient={panel.ingredient}
          onDone={() => {
            setPanel(null);
            reload();
          }}
          onCancel={() => setPanel(null)}
        />
      )}
      <div className="filters">
        <input
          className="field__input"
          placeholder="재료 이름 검색"
          value={keyword}
          onChange={(e) => {
            setPage(0);
            setKeyword(e.target.value);
          }}
        />
      </div>
      <ErrorNotice error={error} />
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>이름</th>
              <th>단위</th>
              <th>연결된 상품 수</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data?.content.map((ingredient) => (
              <tr key={ingredient.ingredientId}>
                <td>{ingredient.ingredientId}</td>
                <td>{ingredient.name}</td>
                <td>{SALE_UNIT[ingredient.unit]}</td>
                <td>{ingredient.linkedProductCount}</td>
                <td>
                  <button type="button" className="chip" onClick={() => setPanel({ ingredient })}>
                    수정
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">재료가 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
