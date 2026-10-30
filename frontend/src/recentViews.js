// 최근 본 레시피·상품은 서버에 저장하지 않고 이 브라우저에만 둔다(회원별로 나눔). 다른 기기에서는 보이지 않는다.
const MAX = 10;
const key = (memberId, type) => `recipick.recent.${memberId}.${type}`;

export function readRecent(memberId, type) {
  try {
    return JSON.parse(localStorage.getItem(key(memberId, type))) ?? [];
  } catch {
    return [];
  }
}

function write(memberId, type, list) {
  try {
    localStorage.setItem(key(memberId, type), JSON.stringify(list));
  } catch {
    // 저장소를 못 쓰면 최근 본 항목만 안 보인다
  }
}

// 같은 항목을 다시 보면 맨 앞으로 옮긴다
export function recordRecent(memberId, type, item) {
  const rest = readRecent(memberId, type).filter((old) => old.id !== item.id);
  write(memberId, type, [item, ...rest].slice(0, MAX));
}

export function clearRecent(memberId, type) {
  write(memberId, type, []);
}
