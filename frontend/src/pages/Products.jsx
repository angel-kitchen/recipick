import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import Pager from '../components/Pager';
import RecentViews from '../components/RecentViews';
import { CardSkeletons } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import usePreviousMartGuard from '../components/usePreviousMartGuard';
import { notifyCartChanged } from '../cartSource';
import { deliveryBlock } from '../delivery';
import { formatAmount, formatWon } from '../labels';

const PAGE_SIZE = 12;

export default function Products() {
  const { member } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [categoryId, setCategoryId] = useState('');
  const [categories, setCategories] = useState([]);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const toast = useToast();
  const { guard, modal } = usePreviousMartGuard();
  const block = deliveryBlock(member);

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
    setError(null);
    try {
      await api.post('/api/cart-items', { productId: product.productId, quantity: 1 });
      notifyCartChanged();
      toast.show(`${product.name}을(를) 장바구니에 담았어요.`, { to: '/cart', label: '장바구니 보기' });
    } catch (e) {
      setError(e);
    }
  };

  // 검색어·카테고리를 화면 상태로 들고 있어서 주소만 옮겨서는 지워지지 않는다. 주소의 검색어도 함께 비운다
  const clearFilters = () => {
    setKeyword('');
    setCategoryId('');
    setPage(0);
    setSearchParams({});
  };

  return (
    <div className="container page">
      <h1 className="page__title">상품</h1>
      {member.mart && <p className="page__sub">이용 마트: {member.mart.name}</p>}
      <RecentViews type="product" title="최근 본 상품" toPath={(id) => `/products/${id}`} />
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
      <ErrorNotice error={error} />
      {!result && !error && <CardSkeletons />}
      {result?.content.length === 0 && (
        <EmptyState icon="product" title="조건에 맞는 상품이 없어요" hint="검색어나 카테고리를 바꿔 보세요." actions={[{ onClick: clearFilters, label: '조건 지우기' }]} />
      )}

      <div className="grid">
        {result?.content.map((product) => (
          <div key={product.productId} className="tile">
            <Link to={`/products/${product.productId}`} className="tile__link">
              {product.imageUrl ? (
                <img className="tile__image" src={product.imageUrl} alt="" loading="lazy" />
              ) : (
                <div className="tile__image">이미지 준비 중</div>
              )}
            </Link>
            <div className="tile__body">
              <Link to={`/products/${product.productId}`} className="tile__title">
                {product.name}
              </Link>
              <span className="tile__meta">
                {product.brand} · {formatAmount(product.saleUnitAmount, product.saleUnit)}
              </span>
              <span className="tile__price">{formatWon(product.price)}</span>
              <div className="tile__actions">
                {product.soldOut ? (
                  <span className="badge badge--warn">품절</span>
                ) : (
                  <button
                    type="button"
                    className="btn btn--outline"
                    disabled={Boolean(block)}
                    title={block?.text}
                    onClick={() => guard(() => addToCart(product))}
                  >
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
