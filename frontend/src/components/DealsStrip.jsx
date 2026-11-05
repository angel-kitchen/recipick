import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import Countdown from './Countdown';
import ImagePlaceholder from './ImagePlaceholder';
import PriceTag from './PriceTag';
import { isDiscountEnded } from '../discount';

// 서버의 검색 결과 캐시(30초)가 끝난 뒤에 다시 불러온다
const REFETCH_AFTER_MS = 31000;

// 지금 할인 중인 상품을 종료가 가까운 순서로 보여 준다. 카운트다운이 끝나면 목록을 다시 불러온다.
// 특가 상품이 없으면 아무것도 그리지 않는다. moreTo(링크)나 onMore(버튼) 중 하나로 "전체 보기"를 연결한다
export default function DealsStrip({ limit = 8, moreTo, onMore }) {
  const [products, setProducts] = useState([]);
  const [reloadKey, setReloadKey] = useState(0);
  const [endedIds, setEndedIds] = useState([]);
  const refetchTimer = useRef(null);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => () => clearTimeout(refetchTimer.current), []);

  const handleEnd = useCallback(
    (productId) => {
      setEndedIds((prev) => (prev.includes(productId) ? prev : [...prev, productId]));
      clearTimeout(refetchTimer.current);
      refetchTimer.current = setTimeout(reload, REFETCH_AFTER_MS);
    },
    [reload],
  );

  useEffect(() => {
    let ignore = false;
    api
      .get('/api/products', { onSale: true, sort: 'discountEndAt,asc', size: limit })
      .then((data) => !ignore && setProducts(data.content))
      .catch(() => !ignore && setProducts([]));
    return () => {
      ignore = true;
    };
  }, [limit, reloadKey]);

  const live = products.filter((product) => !endedIds.includes(product.productId) && !isDiscountEnded(product));
  if (live.length === 0) return null;

  return (
    <section className="deals" aria-label="일일특가">
      <div className="deals__head">
        <h2 className="deals__title">일일특가</h2>
        {moreTo && (
          <Link to={moreTo} className="deals__more">
            특가 전체 보기 ›
          </Link>
        )}
        {onMore && (
          <button type="button" className="deals__more" onClick={onMore}>
            특가 전체 보기 ›
          </button>
        )}
      </div>
      <div className="deals__list">
        {live.map((product) => (
          <Link key={product.productId} to={`/products/${product.productId}`} className="tile deals__item">
            <span className="deals__image">
              {product.imageUrl ? (
                <img className="tile__image" src={product.imageUrl} alt="" loading="lazy" />
              ) : (
                <ImagePlaceholder />
              )}
              {product.discountRate === 50 && <span className="tile__ribbon">반값특가</span>}
            </span>
            <span className="tile__body">
              <span className="tile__title">{product.name}</span>
              <PriceTag price={product.price} salePrice={product.salePrice} discountRate={product.discountRate} />
              {product.discountEndAt && <Countdown endAt={product.discountEndAt} onEnd={() => handleEnd(product.productId)} />}
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
