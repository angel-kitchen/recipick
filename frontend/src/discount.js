import { serverNow } from './api/client';

export const isDiscountEnded = (product) => Boolean(product.discountEndAt) && Date.parse(product.discountEndAt) <= serverNow();

// 끝난 할인(또는 타이머가 끝났다고 알려 준 상품)은 정상가·할인율 0으로 바꿔 돌려준다
export function expireDiscount(product, forced = false) {
  if (!product.discountRate || !(forced || isDiscountEnded(product))) return product;
  return { ...product, salePrice: product.price, discountRate: 0, discountEndAt: null };
}
