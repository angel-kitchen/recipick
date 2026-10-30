import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorNotice from '../components/ErrorNotice';
import { useToast } from '../components/Toast';
import usePreviousMartGuard from '../components/usePreviousMartGuard';
import { PRODUCT_STATUS, formatAmount, formatWon } from '../labels';
import { recordRecent } from '../recentViews';

export default function ProductDetail() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState(null);
  const toast = useToast();
  const { member } = useAuth();
  const { guard, modal } = usePreviousMartGuard();

  useEffect(() => {
    let ignore = false;
    setProduct(null);
    setError(null);
    api
      .get(`/api/products/${productId}`)
      .then((data) => {
        if (ignore) return;
        setProduct(data);
        recordRecent(member.memberId, 'product', { id: data.productId, name: data.name });
      })
      .catch((e) => !ignore && setError(e));
    return () => {
      ignore = true;
    };
  }, [productId]);

  // 담기에 성공하면 true. 바로 구매는 이 값을 보고 장바구니로 넘어간다
  const addToCart = async () => {
    setError(null);
    try {
      await api.post('/api/cart-items', { productId: product.productId, quantity });
      toast.show(`${product.name} ${quantity}개를 장바구니에 담았어요.`, { to: '/cart', label: '장바구니 보기' });
      return true;
    } catch (e) {
      setError(e);
      return false;
    }
  };

  if (!product) {
    return (
      <div className="container page">
        <ErrorNotice error={error} />
        {!error && <p className="state-message">불러오는 중입니다.</p>}
      </div>
    );
  }

  const unavailable = product.soldOut || product.status !== 'SELLING';

  return (
    <div className="container page">
      <p className="page__sub">
        <Link to="/products">← 상품 목록</Link>
      </p>
      <div className="detail">
        <section className="card">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt="" style={{ width: '100%', borderRadius: 12 }} />
          ) : (
            <div className="tile__image">이미지 준비 중</div>
          )}
        </section>
        <section className="card">
          <span className="tile__meta">{product.categoryName}</span>
          <h1 className="page__title">{product.name}</h1>
          <p className="tile__meta">
            {product.brand} · {formatAmount(product.saleUnitAmount, product.saleUnit)}
          </p>
          <p className="tile__price" style={{ fontSize: 28, margin: '12px 0' }}>
            {formatWon(product.price)}
          </p>
          {unavailable && <span className="badge badge--warn">{product.soldOut ? '품절' : PRODUCT_STATUS[product.status]}</span>}
          {product.description && <p style={{ margin: '16px 0', whiteSpace: 'pre-line' }}>{product.description}</p>}
          <ErrorNotice error={error} />
          {!unavailable && (
            <>
              <div className="stepper" style={{ margin: '16px 0' }}>
                <button type="button" aria-label="수량 줄이기" disabled={quantity <= 1} onClick={() => setQuantity(quantity - 1)}>
                  −
                </button>
                <span>{quantity}</span>
                <button type="button" aria-label="수량 늘리기" disabled={quantity >= 99} onClick={() => setQuantity(quantity + 1)}>
                  +
                </button>
              </div>
              <div className="row-actions">
                <button type="button" className="btn btn--outline" onClick={() => guard(addToCart)}>
                  장바구니 담기
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() =>
                    guard(async () => {
                      if (await addToCart()) navigate('/cart');
                    })
                  }
                >
                  바로 구매
                </button>
              </div>
            </>
          )}
        </section>
      </div>
      {modal}
    </div>
  );
}
