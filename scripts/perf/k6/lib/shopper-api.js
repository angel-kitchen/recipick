import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { BASE_URL } from './shopper-config.js';

export const contractFailed = new Rate('contract_failed');

function params(token, name, { phase = 'journey', key = null } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (key) headers['Idempotency-Key'] = key;
    return { headers, timeout: '15s', tags: { name, phase } };
}

function parse(response, name, { phase = 'journey', accepted = [200] } = {}) {
    let envelope = null;
    try { envelope = response.json(); } catch (_) { /* 형식 오류도 계약 실패로 집계한다. */ }
    const ok = accepted.includes(response.status) && envelope?.success === true;
    contractFailed.add(!ok, { name, phase });
    check(response, { [`${name}: 응답 계약`]: () => ok }, { name, phase });
    if (!ok) {
        // 응답 본문·개인정보·인증 정보는 출력하지 않는다.
        const error = new Error(`${name}: HTTP ${response.status}, code=${envelope?.code || 'NO_JSON'}`);
        error.status = response.status;
        error.code = envelope?.code;
        throw error;
    }
    return envelope.data;
}

export function request(method, path, token, body, name, options = {}) {
    const response = http.request(method, `${BASE_URL}${path}`, body == null ? null : JSON.stringify(body),
        params(token, name, options));
    return parse(response, name, options);
}

// 로그인 뒤 서로 독립적인 계정별 준비 조회만 병렬로 보낸다.
export function batchGet(token, queries) {
    const responses = http.batch(queries.map(({ path, name }) => ({
        method: 'GET', url: `${BASE_URL}${path}`, params: params(token, name, { phase: 'setup' }),
    })));
    let firstError = null;
    const data = responses.map((response, i) => {
        try { return parse(response, queries[i].name, { phase: 'setup' }); }
        catch (error) { firstError = firstError || error; return null; }
    });
    if (firstError) throw firstError;
    return data;
}

export function requireState(condition, name) {
    check(null, { [name]: () => Boolean(condition) });
    if (!condition) throw new Error(name);
}
