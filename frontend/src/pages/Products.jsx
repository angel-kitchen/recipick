import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import usePreviousMartGuard from '../components/usePreviousMartGuard';
import { formatAmount, formatWon } from '../labels';

const PAGE_SIZE = 12;

export default function Products() {
  const { member } = useAuth();
  const [searchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [categoryId, setCategoryId] = useState('');
  const [categories, setCategories] = useState([]);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const { guard, modal } = usePreviousMartGuard();

  useEffect(() => {
    api.get('/api/categories').then(setCategories).catch(() => setCategories([]));
  }, []);

  // 레시피 상세에서 재료 이름으로 들어오면 그 검색어를 입력칸에 반영한다
  useEffect(() => {
    setKeyword(searchParams.get('keyword') ?? '');
    setPage(0);
  }, [searchParams]);

  useEffect(() => {
    let ignore = false;
    setError(null);
    api
      .get('/api/products', { keyword, categoryId, page, size: PAGE_SIZE })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [keyword, categoryId, page]);

  const addToCart = async (product) => {
    setMessage(null);
    setError(null);
    try {
      await api.post('/api/cart-items', { productId: product.productId, quantity: 1 });
      setMessage(`${product.name}을(를) 장바구니에 담았습니다.`);
    } catch (e) {
      setError(e);
    }
  };

  return (
    <div className="container page">
      <h1 className="page__title">상품</h1>
      {member?.mart ? (
        <p className="page__sub">이용 마트: {member.mart.name}</p>
      ) : (
        <div className="notice">이용 마트가 정해지지 않아 상품을 담을 수 없습니다. 배송지를 등록하면 가까운 마트가 정해집니다.</div>
      )}
      <div className="filters">
        <input
          className="field__input"
          placeholder="상품 이름 검색"
          value={keyword}
          onChange={(e) => {
            setPage(0);
            setKeyword(e.target.value);
          }}
        />
        <select
          className="field__input"
          value={categoryId}
          onChange={(e) => {
            setPage(0);
            setCategoryId(e.target.value);
          }}
        >
          <option value="">전체 카테고리</option>
          {categories.map((category) => (
            <option key={category.categoryId} value={category.categoryId}>
              {category.name}
            </option>
          ))}
        </select>
      </div>

      {modal}
      {message && <div className="notice notice--info">{message}</div>}
      <ErrorNotice error={error} />
      {!result && !error && <p className="state-message">불러오는 중입니다.</p>}
      {result?.content.length === 0 && <p className="state-message">조건에 맞는 상품이 없습니다.</p>}

      <div className="grid">
        {result?.content.map((product) => (
          <div key={product.productId} className="tile">
            {product.imageUrl ? (
              <img className="tile__image" src={product.imageUrl} alt="" loading="lazy" />
            ) : (
              <div className="tile__image">이미지 준비 중</div>
            )}
            <div className="tile__body">
              <span className="tile__title">{product.name}</span>
              <span className="tile__meta">
                {product.brand} · {formatAmount(product.saleUnitAmount, product.saleUnit)}
              </span>
              <span className="tile__price">{formatWon(product.price)}</span>
              <div className="tile__actions">
                {product.soldOut ? (
                  <span className="badge badge--warn">품절</span>
                ) : (
                  <button type="button" className="btn btn--outline" onClick={() => guard(() => addToCart(product))}>
                    담기
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
      {result && <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </div>
  );
}
