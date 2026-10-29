import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import ErrorNotice from '../../components/ErrorNotice';
import Pager from '../../components/Pager';
import { PRODUCT_STATUS, SALE_UNIT, STOCK_REASON, formatDateTime, formatWon } from '../../labels';
import { useAdmin } from '../AdminContext';
import useLoad from '../useLoad';

const EMPTY = {
  martId: '',
  categoryId: '',
  ingredientId: '',
  name: '',
  brand: '',
  description: '',
  saleUnitAmount: '',
  saleUnit: 'G',
  imageUrl: '',
  price: '',
};

const blankToNull = (value) => (value === '' || value === undefined ? null : value);

function ProductForm({ product, marts, categories, ingredients, isSuper, martId, onDone, onCancel }) {
  const editing = Boolean(product);
  const [form, setForm] = useState(
    editing
      ? {
          ...EMPTY,
          categoryId: String(product.categoryId),
          ingredientId: product.ingredientId ? String(product.ingredientId) : '',
          name: product.name,
          brand: product.brand ?? '',
          saleUnitAmount: String(product.saleUnitAmount),
          saleUnit: product.saleUnit,
          imageUrl: product.imageUrl ?? '',
        }
      : { ...EMPTY, martId: String(martId ?? marts[0]?.martId ?? '') },
  );
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const change = (key) => (e) => setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    const common = {
      categoryId: Number(form.categoryId),
      ingredientId: form.ingredientId ? Number(form.ingredientId) : null,
      name: form.name,
      brand: blankToNull(form.brand),
      description: blankToNull(form.description),
      saleUnitAmount: form.saleUnitAmount,
      saleUnit: form.saleUnit,
      imageUrl: blankToNull(form.imageUrl),
    };
    try {
      if (editing) await api.put(`/api/admin/products/${product.productId}`, common);
      else await api.post('/api/admin/products', { ...common, martId: Number(form.martId), price: Number(form.price) });
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">{editing ? '상품 수정' : '상품 등록'}</h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        {!editing && (
          <label className="field">
            <span className="field__label">마트</span>
            <select className="field__input" value={form.martId} onChange={change('martId')} disabled={!isSuper} required>
              {marts.map((mart) => (
                <option key={mart.martId} value={mart.martId}>
                  {mart.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <label className="field">
          <span className="field__label">카테고리</span>
          <select className="field__input" value={form.categoryId} onChange={change('categoryId')} required>
            <option value="">선택</option>
            {categories.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field__label">연결 재료 (자동 장바구니용)</span>
          {isSuper ? (
            <select className="field__input" value={form.ingredientId} onChange={change('ingredientId')}>
              <option value="">연결 안 함</option>
              {ingredients.map((ingredient) => (
                <option key={ingredient.ingredientId} value={ingredient.ingredientId}>
                  {ingredient.name} ({SALE_UNIT[ingredient.unit]})
                </option>
              ))}
            </select>
          ) : (
            <input className="field__input" inputMode="numeric" value={form.ingredientId} onChange={change('ingredientId')} />
          )}
        </label>
        <label className="field">
          <span className="field__label">상품명</span>
          <input className="field__input" value={form.name} onChange={change('name')} maxLength={50} required />
        </label>
        <label className="field">
          <span className="field__label">브랜드</span>
          <input className="field__input" value={form.brand} onChange={change('brand')} maxLength={50} />
        </label>
        <label className="field">
          <span className="field__label">판매 단위 수량</span>
          <input
            className="field__input"
            inputMode="decimal"
            value={form.saleUnitAmount}
            onChange={(e) => setForm((p) => ({ ...p, saleUnitAmount: onlyDecimal(e.target.value) }))}
            required
          />
        </label>
        <label className="field">
          <span className="field__label">판매 단위</span>
          <select className="field__input" value={form.saleUnit} onChange={change('saleUnit')}>
            {Object.entries(SALE_UNIT).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        {!editing && (
          <label className="field">
            <span className="field__label">가격(원)</span>
            <input className="field__input" inputMode="numeric" value={form.price} onChange={change('price')} required />
          </label>
        )}
        <label className="field">
          <span className="field__label">이미지 주소</span>
          <input className="field__input" value={form.imageUrl} onChange={change('imageUrl')} maxLength={255} placeholder="/images/products/egg.jpg" />
        </label>
      </div>
      <label className="field">
        <span className="field__label">설명</span>
        <input className="field__input" value={form.description} onChange={change('description')} />
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

const EMPTY_STOCK = { type: 'INCREASE', quantity: '', reason: '', note: '' };

// 숫자와 소수점 하나만 남긴다
const onlyDecimal = (value) => value.replace(/[^\d.]/g, '').replace(/^(\d*\.?\d*).*$/, '$1');

function StockPanel({ product, onDone, onCancel }) {
  const [form, setForm] = useState(EMPTY_STOCK);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const { data: history, reload } = useLoad(
    () => api.get(`/api/admin/products/${product.productId}/stock-adjustments`, { size: 10 }),
    [product.productId],
  );

  const stepQuantity = (delta) =>
    setForm((p) => ({ ...p, quantity: String(Math.max(0, Number(p.quantity || 0) + delta)) }));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await api.post(`/api/admin/products/${product.productId}/stock-adjustments`, {
        type: form.type,
        quantity: Number(form.quantity),
        reason: form.reason === 'OTHER' ? form.note.trim() : STOCK_REASON[form.reason],
      });
      setForm(EMPTY_STOCK);
      reload();
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="card form admin__section" onSubmit={submit}>
      <h2 className="tile__title">
        재고 조정 · {product.name} (현재 {product.totalQuantity}개)
      </h2>
      <ErrorNotice error={error} />
      <div className="form-grid">
        <label className="field">
          <span className="field__label">구분</span>
          <select className="field__input" value={form.type} onChange={(e) => setForm((p) => ({ ...p, type: e.target.value }))}>
            <option value="INCREASE">증가</option>
            <option value="DECREASE">감소</option>
          </select>
        </label>
        <label className="field">
          <span className="field__label">수량</span>
          <div className="stepper">
            <button type="button" aria-label="수량 줄이기" disabled={!form.quantity} onClick={() => stepQuantity(-1)}>
              −
            </button>
            <input
              className="stepper__input"
              inputMode="numeric"
              value={form.quantity}
              placeholder="0"
              onChange={(e) => setForm((p) => ({ ...p, quantity: e.target.value.replace(/\D/g, '').slice(0, 9) }))}
              required
            />
            <button type="button" aria-label="수량 늘리기" onClick={() => stepQuantity(1)}>
              +
            </button>
          </div>
        </label>
        <label className="field">
          <span className="field__label">사유</span>
          <select className="field__input" value={form.reason} onChange={(e) => setForm((p) => ({ ...p, reason: e.target.value }))} required>
            <option value="">사유 선택</option>
            {Object.entries(STOCK_REASON).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
          {form.reason === 'OTHER' && (
            <input
              className="field__input"
              style={{ marginTop: 8 }}
              placeholder="사유를 직접 입력"
              value={form.note}
              maxLength={500}
              onChange={(e) => setForm((p) => ({ ...p, note: e.target.value }))}
              required
            />
          )}
        </label>
      </div>
      <div className="row-actions" style={{ marginTop: 0 }}>
        <button type="submit" className="btn" disabled={saving}>
          조정
        </button>
        <button type="button" className="btn btn--outline" onClick={onCancel}>
          닫기
        </button>
      </div>
      {history?.content.length > 0 && (
        <details>
          <summary>최근 조정 이력</summary>
          <ul className="list-plain">
            {history.content.map((item) => (
              <li key={`${item.createdAt}-${item.beforeQuantity}`}>
                <span>
                  {item.type === 'INCREASE' ? '+' : '-'}
                  {item.quantity} (이전 {item.beforeQuantity}) {item.reason}
                </span>
                <span>{formatDateTime(item.createdAt)}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </form>
  );
}

export default function AdminProducts() {
  const { admin } = useAdmin();
  const isSuper = admin.role === 'SUPER_ADMIN';
  const [filter, setFilter] = useState({ keyword: '', categoryId: '', status: '', martId: '' });
  const [page, setPage] = useState(0);
  const [panel, setPanel] = useState(null);
  const [actionError, setActionError] = useState(null);
  const { data, error, reload } = useLoad(() => api.get('/api/admin/products', { ...filter, page, size: 20 }), [filter, page]);
  const [refs, setRefs] = useState({ marts: [], categories: [], ingredients: [] });

  useEffect(() => {
    Promise.all([
      api.get('/api/admin/marts'),
      api.get('/api/categories'),
      isSuper ? api.get('/api/admin/ingredients', { size: 100 }) : Promise.resolve(null),
    ])
      .then(([marts, categories, ingredients]) =>
        setRefs({ marts, categories, ingredients: ingredients?.content ?? [] }),
      )
      .catch(() => {});
  }, [isSuper]);

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

  const changePrice = (product) => {
    const input = window.prompt(`${product.name}의 새 가격(원)`, String(product.price));
    if (input === null || input.trim() === '') return;
    act(() => api.patch(`/api/admin/products/${product.productId}/price`, { price: Number(input) }));
  };

  const remove = (product) => {
    if (!window.confirm(`'${product.name}' 상품을 삭제할까요?`)) return;
    act(() => api.delete(`/api/admin/products/${product.productId}`));
  };

  return (
    <>
      <div className="admin__head">
        <h1 className="page__title">상품·재고</h1>
        <button type="button" className="btn" onClick={() => setPanel({ type: 'form' })}>
          상품 등록
        </button>
      </div>
      {panel?.type === 'form' && (
        <ProductForm
          key={panel.product?.productId ?? 'new'}
          product={panel.product}
          marts={refs.marts}
          categories={refs.categories}
          ingredients={refs.ingredients}
          isSuper={isSuper}
          martId={admin.martId}
          onDone={() => {
            setPanel(null);
            reload();
          }}
          onCancel={() => setPanel(null)}
        />
      )}
      {panel?.type === 'stock' && (
        <StockPanel key={panel.product.productId} product={panel.product} onDone={reload} onCancel={() => setPanel(null)} />
      )}
      <div className="filters">
        <input className="field__input" placeholder="상품명 검색" value={filter.keyword} onChange={setFilterValue('keyword')} />
        <select className="field__input" value={filter.categoryId} onChange={setFilterValue('categoryId')}>
          <option value="">카테고리 전체</option>
          {refs.categories.map((category) => (
            <option key={category.categoryId} value={category.categoryId}>
              {category.name}
            </option>
          ))}
        </select>
        <select className="field__input" value={filter.status} onChange={setFilterValue('status')}>
          <option value="">상태 전체</option>
          {Object.entries(PRODUCT_STATUS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        {isSuper && (
          <select className="field__input" value={filter.martId} onChange={setFilterValue('martId')}>
            <option value="">마트 전체</option>
            {refs.marts.map((mart) => (
              <option key={mart.martId} value={mart.martId}>
                {mart.name}
              </option>
            ))}
          </select>
        )}
      </div>
      <ErrorNotice error={error ?? actionError} />
      <div className="card table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th />
              <th>상품명</th>
              <th>브랜드</th>
              <th>카테고리</th>
              <th>마트</th>
              <th>가격</th>
              <th>재고</th>
              <th>상태</th>
              <th>관리</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((product) => (
              <tr key={product.productId}>
                <td>{product.imageUrl ? <img className="table__thumb" src={product.imageUrl} alt="" /> : <div className="table__thumb" />}</td>
                <td>{product.name}</td>
                <td>{product.brand ?? '-'}</td>
                <td>{product.categoryName}</td>
                <td>{refs.marts.find((mart) => mart.martId === product.martId)?.name ?? product.martId}</td>
                <td>{formatWon(product.price)}</td>
                <td>{product.totalQuantity}</td>
                <td>{PRODUCT_STATUS[product.status]}</td>
                <td>
                  <span className="chips">
                    <button type="button" className="chip" onClick={() => setPanel({ type: 'form', product })}>
                      수정
                    </button>
                    <button type="button" className="chip" onClick={() => changePrice(product)}>
                      가격
                    </button>
                    <button type="button" className="chip" onClick={() => setPanel({ type: 'stock', product })}>
                      재고
                    </button>
                    <button
                      type="button"
                      className={`chip ${product.status === 'SELLING' ? 'chip--solid' : ''}`}
                      onClick={() =>
                        act(() =>
                          api.patch(`/api/admin/products/${product.productId}/status`, {
                            status: product.status === 'SELLING' ? 'STOPPED' : 'SELLING',
                          }),
                        )
                      }
                    >
                      {product.status === 'SELLING' ? '판매 중지' : '판매 재개'}
                    </button>
                    <button type="button" className="chip" onClick={() => remove(product)}>
                      삭제
                    </button>
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && data.content.length === 0 && <p className="state-message">상품이 없습니다.</p>}
      </div>
      {data && <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  );
}
