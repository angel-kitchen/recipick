import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL } from './shopper-config.js';

export const contractFailed = new Rate('contract_failed');

// 토큰·본문·개인정보는 로그에 출력하지 않는다. URL의 개별 ID 대신 고정 name 태그로 집계한다.
export function request(method, path, token, body, name, phase = 'journey', accepted = [200], key = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (key) headers['Idempotency-Key'] = key;
  const response = http.request(method, `${BASE_URL}${path}`, body == null ? null : JSON.stringify(body), {
    headers, timeout: '15s', tags: { name, phase },
  });
  let envelope = null;
  try { envelope = response.json(); } catch (_) { /* 형식 오류도 아래 계약 실패로 집계한다. */ }
  const ok = accepted.includes(response.status) && envelope?.success === true;
  contractFailed.add(!ok, { name, phase });
  check(response, { [`${name}: 응답 계약`]: () => ok }, { name, phase });
  if (!ok) {
    const error = new Error(`${name}: HTTP ${response.status}, code=${envelope?.code || 'NO_JSON'}`);
    error.status = response.status;
    throw error;
  }
  return envelope.data;
}

export function requireState(condition, name) {
  check(null, { [name]: () => Boolean(condition) });
  if (!condition) throw new Error(name);
}
