// 서버 장바구니 응답에는 "어느 레시피의 재료인지"가 없어서, 자동으로 담을 때 이 브라우저에 기억해 두고 장바구니에 표시한다.
// 상품이 장바구니에서 사라지면 기억도 지운다. 다른 브라우저에서는 보이지 않는다(서버 필드가 생기면 대체).
const key = (memberId) => `recipick.cartSource.${memberId ?? 'me'}`;

function read(memberId) {
  try {
    return JSON.parse(localStorage.getItem(key(memberId))) ?? {};
  } catch {
    return {};
  }
}

function write(memberId, value) {
  try {
    localStorage.setItem(key(memberId), JSON.stringify(value));
  } catch {
    // 저장소를 못 쓰면 라벨만 안 보인다
  }
}

export function rememberSources(memberId, productIds, recipeNames) {
  const all = read(memberId);
  productIds.forEach((productId) => {
    const merged = new Set([...(all[productId] ?? []), ...recipeNames]);
    all[productId] = [...merged];
  });
  write(memberId, all);
}

// 장바구니에 남아 있는 상품의 기억만 돌려주고 나머지는 지운다
export function readSources(memberId, presentProductIds) {
  const all = read(memberId);
  const present = new Set(presentProductIds.map(String));
  const kept = Object.fromEntries(Object.entries(all).filter(([productId]) => present.has(productId)));
  if (Object.keys(kept).length !== Object.keys(all).length) write(memberId, kept);
  return kept;
}
