import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL } from './shopper-config.js';

export const contractFailed = new Rate('contract_failed');
const wallTime = new Trend('request_wall_time', true);
let sequence = 0;

function requestContext() {
    const startedAt = Date.now();
    const vu = typeof __VU === 'number' ? __VU : 0;
    const iteration = typeof __ITER === 'number' ? __ITER : -1;
    return { startedAt, vu, iteration, clientRequestId: `perf-${vu}-${iteration}-${++sequence}-${startedAt}` };
}

function failureDetails(response, name, phase, context, endedAt) {
    const timingNames = ['blocked', 'looking_up', 'connecting', 'tls_handshaking', 'sending', 'waiting', 'receiving', 'duration'];
    const timings = {};
    for (const field of timingNames) {
        const value = response.timings?.[field];
        timings[field] = Number.isFinite(value) ? value : null;
    }
    const serverId = Object.entries(response.headers || {})
        .find(([key]) => key.toLowerCase() === 'x-request-id')?.[1];
    return { event: 'request_failure', name, phase, vu: context.vu, iteration: context.iteration,
        clientRequestId: context.clientRequestId,
        startedAt: new Date(context.startedAt).toISOString(), endedAt: new Date(endedAt).toISOString(),
        wallTimeMs: endedAt - context.startedAt, status: response.status,
        errorCode: Number.isFinite(response.error_code) ? response.error_code : null, timings,
        serverRequestId: typeof serverId === 'string' && /^[0-9a-f-]{36}$/i.test(serverId) ? serverId : null };
}

function params(token, name, { phase = 'journey', key = null } = {}, context) {
    const headers = { 'Content-Type': 'application/json', 'X-Perf-Request-Id': context.clientRequestId };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (key) headers['Idempotency-Key'] = key;
    return { headers, timeout: '15s', tags: { name, phase } };
}

function parse(response, name, { phase = 'journey', accepted = [200] } = {}, context) {
    const endedAt = Date.now();
    wallTime.add(endedAt - context.startedAt, { name, phase });
    let envelope = null;
    try { envelope = response.json(); } catch (_) { /* 형식 오류도 계약 실패로 집계한다. */ }
    const ok = accepted.includes(response.status) && envelope?.success === true;
    contractFailed.add(!ok, { name, phase });
    check(response, { [`${name}: 응답 계약`]: () => ok }, { name, phase });
    if (!ok) {
        // URL·쿼리·본문·토큰·쿠키는 기록하지 않고 고정 이름과 연결 시간만 남긴다.
        console.error(JSON.stringify(failureDetails(response, name, phase, context, endedAt)));
        const error = new Error(`${name}: HTTP ${response.status}, code=${typeof envelope?.code === 'string' && /^[A-Z][A-Z0-9_-]{0,63}$/.test(envelope.code) ? envelope.code : 'NO_CODE'}`);
        error.status = response.status;
        error.code = envelope?.code;
        throw error;
    }
    return envelope.data;
}

export function request(method, path, token, body, name, options = {}) {
    const context = requestContext();
    const response = http.request(method, `${BASE_URL}${path}`, body == null ? null : JSON.stringify(body),
        params(token, name, options, context));
    return parse(response, name, options, context);
}

// 로그인 뒤 서로 독립적인 계정별 준비 조회만 병렬로 보낸다.
export function batchGet(token, queries) {
    const contexts = queries.map(() => requestContext());
    const responses = http.batch(queries.map(({ path, name }, i) => ({
        method: 'GET', url: `${BASE_URL}${path}`, params: params(token, name, { phase: 'setup' }, contexts[i]),
    })));
    let firstError = null;
    const data = responses.map((response, i) => {
        try { return parse(response, queries[i].name, { phase: 'setup' }, contexts[i]); }
        catch (error) { firstError = firstError || error; return null; }
    });
    if (firstError) throw firstError;
    return data;
}

export function requireState(condition, name) {
    check(null, { [name]: () => Boolean(condition) });
    if (!condition) throw new Error(name);
}
