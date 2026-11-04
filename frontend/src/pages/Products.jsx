import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import EmptyState from '../components/EmptyState';
import ErrorNotice from '../components/ErrorNotice';
import ImagePlaceholder from '../components/ImagePlaceholder';
import Countdown from '../components/Countdown';
import DealsStrip from '../components/DealsStrip';
import PriceTag from '../components/PriceTag';
import Pager from '../components/Pager';
import RecentViews from '../components/RecentViews';
import { CardSkeletons } from '../components/Skeleton';
import { useToast } from '../components/Toast';
import usePreviousMartGuard from '../components/usePreviousMartGuard';
import { notifyCartChanged } from '../cartSource';
import { deliveryBlock } from '../delivery';
import { formatAmount } from '../labels';

const PAGE_SIZE = 12;

// 서버는 최소·최대 가격 한 구간만 받으므로 가격대는 하나만 고른다
const PRICE_RANGES = [
  { key: 'low', label: '5,000원 미만', maxPrice: 4999 },
  { key: 'mid', label: '5,000 ~ 10,000원', minPrice: 5000, maxPrice: 10000 },
  { key: 'high', label: '10,000원 이상', minPrice: 10000 },
];

export default function Products() {
  const { member } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('keyword') ?? '');
  const [categoryId, setCategoryId] = useState('');
  const [categories, setCategories] = useState([]);
  const [priceKey, setPriceKey] = useState('');
  const [sort, setSort] = useState('createdAt,desc');
  const [onlyAvailable, setOnlyAvailable] = useState(false);
  const [onSaleOnly, setOnSaleOnly] = useState(false);
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

  const priceRange = PRICE_RANGES.find((range) => range.key === priceKey);

  useEffect(() => {
    let ignore = false;
    setError(null);
    api
      .get('/api/products', {
        keyword,
        categoryId,
        minPrice: priceRange?.minPrice,
        maxPrice: priceRange?.maxPrice,
        onSale: onSaleOnly || undefined,
        page,
        size: PAGE_SIZE,
        sort,
      })
      .then((data) => !ignore && setResult(data))
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [keyword, categoryId, priceRange, onSaleOnly, page, sort]);

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
    setPriceKey('');
    setOnlyAvailable(false);
    setOnSaleOnly(false);
    setPage(0);
    setSearchParams({});
  };

  const visible = result?.content.filter((product) => !onlyAvailable || (!product.soldOut && product.status !== 'STOPPED'));

  return (
    <div className="container page">
      <h1 className="page__title">상품</h1>
      <div className="row-actions"><Link to="/products/best" className="btn btn--outline">베스트 상품</Link><select className="field__input" aria-label="상품 정렬" value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }}><option value="createdAt,desc">최신순</option><option value="price,asc">낮은 가격순</option><option value="price,desc">높은 가격순</option></select></div>
      {member.mart && <p className="page__sub">이용 마트: {member.mart.name}</p>}
      <RecentViews type="product" title="최근 본 상품" toPath={(id) => `/products/${id}`} />
      {!onSaleOnly && (
        <DealsStrip
          limit={4}
          onMore={() => {
            setOnSaleOnly(true);
            setPage(0);
          }}
        />
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
      </div>

      {modal}
      <div className="catalog">
        <aside className="catalog__side" aria-label="상품 조건">
          <section className="side-group">
            <h2 className="side-group__title">카테고리</h2>
            <ul className="side-list">
              {[{ categoryId: '', name: '전체' }, ...categories].map((category) => (
                <li key={category.categoryId}>
                  <button
                    type="button"
                    className={`side-list__item${String(category.categoryId) === String(categoryId) ? ' is-active' : ''}`}
                    aria-pressed={String(category.categoryId) === String(categoryId)}
                    onClick={() => {
                      setPage(0);
                      setCategoryId(category.categoryId);
                    }}
                  >
                    {category.name}
                  </button>
                </li>
              ))}
            </ul>
          </section>
          <section className="side-group">
            <h2 className="side-group__title">가격대</h2>
            {PRICE_RANGES.map((range) => (
              <label key={range.key} className="check">
                <input
                  type="checkbox"
                  checked={priceKey === range.key}
                  onChange={(e) => {
                    setPage(0);
                    setPriceKey(e.target.checked ? range.key : '');
                  }}
                />
                {range.label}
              </label>
            ))}
          </section>
          <section className="side-group">
            <h2 className="side-group__title">특가</h2>
            <label className="check">
              <input
                type="checkbox"
                checked={onSaleOnly}
                onChange={(e) => {
                  setOnSaleOnly(e.target.checked);
                  setPage(0);
                }}
              />
              특가 상품만 보기
            </label>
          </section>
          <section className="side-group">
            <h2 className="side-group__title">판매 상태</h2>
            <label className="check">
              <input type="checkbox" checked={onlyAvailable} onChange={(e) => setOnlyAvailable(e.target.checked)} />
              구매 가능한 상품만
            </label>
          </section>
        </aside>

        <div className="catalog__main">
          <ErrorNotice error={error} />
          {!result && !error && <CardSkeletons />}
          {visible?.length === 0 && (
            <EmptyState icon="product" title="조건에 맞는 상품이 없어요" hint="검색어나 조건을 바꿔 보세요." actions={[{ onClick: clearFilters, label: '조건 지우기' }]} />
          )}

          <div className="grid">
            {visible?.map((product) => (
              <div key={product.productId} className="tile">
                <Link to={`/products/${product.productId}`} className="tile__link">
                  {product.imageUrl ? (
                    <img className="tile__image" src={product.imageUrl} alt="" loading="lazy" />
                  ) : (
                    <ImagePlaceholder />
                  )}
                  {product.discountRate === 50 && <span className="tile__ribbon">반값특가</span>}
                </Link>
                <div className="tile__body">
                  <Link to={`/products/${product.productId}`} className="tile__title">
                    {product.name}
                  </Link>
                  <span className="tile__meta">
                    {product.brand} · {formatAmount(product.saleUnitAmount, product.saleUnit)}
                  </span>
                  <PriceTag price={product.price} salePrice={product.salePrice} discountRate={product.discountRate} />
                  {product.discountEndAt && <Countdown endAt={product.discountEndAt} />}
                  <div className="tile__actions">
                    {product.soldOut ? (
                      <span className="badge badge--warn">품절</span>
                    ) : (
                      <button
                        type="button"
                        className="btn btn--outline"
                        disabled={Boolean(block) || product.status === 'STOPPED'}
                        title={product.status === 'STOPPED' ? '판매 중지 상품이에요' : block?.text}
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
      </div>
    </div>
  );
}
