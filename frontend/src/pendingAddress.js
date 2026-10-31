import { api } from './api/client';

// 가입하기 전에 확인한 주소를 가입·로그인이 끝날 때까지 이 탭에만 보관한다. 저장이 끝나면 지운다
const KEY = 'recipick.pendingAddress';

export function readPendingAddress() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const value = JSON.parse(raw);
    const valid = value?.address && value?.detailAddress && /^\d{10}$/.test(value?.legalDongCode ?? '');
    return valid ? value : null;
  } catch {
    // 저장소를 못 쓰거나 값이 깨졌으면 보관한 주소가 없는 것으로 본다
    return null;
  }
}

export function savePendingAddress({ address, detailAddress, legalDongCode }) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ address, detailAddress, legalDongCode }));
    return true;
  } catch {
    // 저장소를 못 쓰면 호출한 쪽이 가입으로 넘어가지 않고 이유를 알려야 한다
    return false;
  }
}

export function clearPendingAddress() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // 지울 수 없어도 다음 저장 때 덮어쓰므로 넘어간다
  }
}

// 위치 이용에 동의하고 확정한 주소를 현재 배송지로 저장한다(이용 마트도 서버가 정한다)
export function registerAddress({ address, detailAddress, legalDongCode }) {
  return api.post('/api/members/me/addresses', {
    label: '',
    address,
    detailAddress,
    legalDongCode,
    select: true,
    locationAgreed: true,
  });
}
