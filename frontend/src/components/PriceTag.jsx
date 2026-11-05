import { formatWon } from '../labels';

// price는 정상가, salePrice는 지금 적용되는 판매가다. 할인 중이 아니면 정상가만 보여 준다
export default function PriceTag({ price, salePrice, discountRate, large = false }) {
  const discounted = discountRate > 0 && salePrice != null && salePrice < price;
  if (!discounted) {
    return <span className={`tile__price${large ? ' price-tag--large' : ''}`}>{formatWon(price)}</span>;
  }
  return (
    <span className={`price-tag${large ? ' price-tag--large' : ''}`}>
      <s className="price-tag__original">{formatWon(price)}</s>
      <span className="price-tag__line">
        <span className="price-tag__rate">{discountRate}%</span>
        <span className="tile__price">{formatWon(salePrice)}</span>
      </span>
    </span>
  );
}
