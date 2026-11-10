// AI 대화와 검색 결과를 브라우저 탭 안에서만 기억한다. 상세 화면에 갔다가 뒤로 와도 같은 결과를 다시 보여 줘서
// AI를 또 부르지 않게 하려는 것이다. 탭을 닫거나 로그아웃하면 사라지고, 다른 회원의 기억은 읽지 않는다.
// 저장 공간을 못 쓰는 브라우저(사생활 보호 모드 등)에서는 기억 없이 그냥 동작한다.
// 저장 모양을 바꾸면 키의 버전(v1)을 올려, 이전 모양으로 남은 기억을 읽다가 화면이 깨지지 않게 한다
const KEYS = { chat: 'recipick.recipe-chat.v2', detail: 'recipick.recipe-detail-search.v1' };

// 화면이 결과에서 배열로 쓰는 항목이 배열이 아니면 기억이 깨진 것이다
const LIST_FIELDS = ['recipes', 'menus', 'meals'];

export function isValidResult(result) {
  if (result == null) return true;
  return typeof result === 'object' && LIST_FIELDS.every((key) => result[key] == null || Array.isArray(result[key]));
}

// isValid가 거절하거나 읽을 수 없으면 null이다. 호출하는 쪽은 "기억 없음"으로 시작하면 된다
export function readMemory(name, memberId, isValid) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEYS[name]));
    return saved && saved.memberId === memberId && isValid(saved.data) ? saved.data : null;
  } catch {
    return null;
  }
}

export function writeMemory(name, memberId, data) {
  try {
    sessionStorage.setItem(KEYS[name], JSON.stringify({ memberId, data }));
  } catch {
    // 기억하지 못해도 화면은 그대로 쓸 수 있다
  }
}

export function clearMemory(name) {
  try {
    (name ? [KEYS[name]] : Object.values(KEYS)).forEach((key) => sessionStorage.removeItem(key));
  } catch {
    // 위와 같다
  }
}
