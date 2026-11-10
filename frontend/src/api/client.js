// 액세스는 주체별 메모리에만 보관한다. 리프레시 Cookie와 갱신 경로도 분리한다.
let accessToken = null;
let adminToken = null;
let adminSession = null;
let onAuthLost = () => {};
let onAdminAuthLost = () => {};
const reissuing = new Map();
const restoring = new Map();
let csrf = null;
let csrfLoading = null;
const isAdminPath = (path) => path.startsWith('/api/admin/');
const authPath = (type, action) => type === 'ADMIN' ? `/api/admin/auth/${action}` : `/api/auth/${action}`;
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('recipick-auth-v2') : null;

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || '요청을 처리하지 못했어요.');
    this.status = status;
    this.code = body?.code;
    this.errors = body?.errors ?? [];
  }
}

export const isUnknownOutcome = (e) => !(e instanceof ApiError) || e.status >= 500;
export function setAuthLostHandler(handler) { onAuthLost = handler; }
export function setAdminAuthLostHandler(handler) { onAdminAuthLost = handler; }

function readClaims(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

export function setAccessToken(token) { accessToken = token; }
export function setAdminToken(token, session = null) {
  adminToken = token;
  adminSession = token ? session ?? adminSessionFrom(token) : null;
}
function adminSessionFrom(token) {
  const claims = readClaims(token);
  return claims ? { adminId: claims.adminId, role: claims.role, martId: claims.martId ?? null } : null;
}
export function getAdminSession() { return adminSession; }

function loseSession(type) {
  restoring.delete(type);
  if (type === 'ADMIN') {
    setAdminToken(null);
    onAdminAuthLost();
  } else {
    setAccessToken(null);
    onAuthLost();
  }
}

if (channel) {
  channel.onmessage = ({ data }) => {
    if (data?.event !== 'LOGOUT' || !['MEMBER', 'ADMIN'].includes(data.type)) return;
    const token = data.type === 'ADMIN' ? adminToken : accessToken;
    // 다른 계정으로 새로 로그인한 탭의 세션까지 지우지 않는다.
    if (!data.sid || readClaims(token)?.sid === data.sid) loseSession(data.type);
  };
}

// 브라우저 탭끼리도 같은 주체의 Cookie 회전을 순서화한다. 토큰을 storage에 저장하지 않는다.
function withSessionLock(type, work) {
  return globalThis.navigator?.locks?.request
    ? globalThis.navigator.locks.request(`recipick-auth-${type}`, work)
    : work();
}

function buildUrl(path, query) {
  if (!query) return path;
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.append(key, value);
  });
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

const cookieActions = new Set([
  '/api/auth/signup', '/api/auth/login', '/api/auth/reissue', '/api/auth/logout',
  '/api/admin/auth/login', '/api/admin/auth/reissue', '/api/admin/auth/logout',
]);
async function csrfToken() {
  if (csrf) return csrf;
  if (!csrfLoading) {
    csrfLoading = fetch('/api/auth/csrf', { credentials: 'same-origin' }).then(parse)
      .then((data) => { csrf = data; return data; })
      .finally(() => { csrfLoading = null; });
  }
  return csrfLoading;
}

async function send(method, path, { body, headers, query, admin, credentials = 'same-origin', csrfRetried = false } = {}) {
  const finalHeaders = { ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  const token = admin || isAdminPath(path) ? adminToken : accessToken;
  if (token) finalHeaders.Authorization = `Bearer ${token}`;
  if (method === 'POST' && cookieActions.has(path)) {
    // 다른 탭이 CSRF Cookie를 처음 만든 경우도 현재 Cookie에 대응하는 값을 다시 받는다.
    csrf = null;
    const protection = await csrfToken();
    finalHeaders[protection.headerName] = protection.token;
  }
  const response = await fetch(buildUrl(path, query), {
    method, headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials,
  });
  if (!csrfRetried && method === 'POST' && cookieActions.has(path) && response.status === 403) {
    const error = await response.clone().json().catch(() => null);
    if (error?.code === 'AUTH-013') {
      csrf = null;
      return send(method, path, { body, headers, query, admin, credentials, csrfRetried: true });
    }
  }
  return response;
}

let serverOffsetMs = 0;
export const serverNow = () => Date.now() + serverOffsetMs;
function syncServerClock(res) {
  const serverTime = Date.parse(res.headers.get('Date') ?? '');
  if (!Number.isNaN(serverTime)) serverOffsetMs = serverTime - Date.now();
}
async function parse(res) {
  syncServerClock(res);
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* 프록시 일반 텍스트 응답은 JSON이 아니다. */ }
  if (!json && res.status >= 502) {
    throw new ApiError(res.status, { message: '서버에 연결하지 못했어요. 잠시 후 처리 결과를 확인해 주세요.' });
  }
  if (!res.ok || json?.success === false) throw new ApiError(res.status, json);
  return json?.data ?? null;
}

export function reissueToken(type = 'MEMBER') {
  if (!reissuing.has(type)) {
    const pending = withSessionLock(type, async () => {
      const data = await parse(await send('POST', authPath(type, 'reissue')));
      if (readClaims(data.accessToken)?.type !== type) {
        throw new ApiError(401, { code: 'AUTH-011', message: '다시 로그인해 주세요.' });
      }
      if (type === 'ADMIN') setAdminToken(data.accessToken);
      else setAccessToken(data.accessToken);
      return type;
    }).finally(() => reissuing.delete(type));
    reissuing.set(type, pending);
  }
  return reissuing.get(type);
}

export function restoreSession(type = 'MEMBER') {
  if (!restoring.has(type)) {
    restoring.set(type, reissueToken(type).catch((e) => {
      if (e.status === 401 || (e.status === 403 && e.code === 'AUTH-009')) return null;
      restoring.delete(type);
      throw e;
    }));
  }
  return restoring.get(type);
}

export async function logoutSession(type = 'MEMBER') {
  const original = readClaims(type === 'ADMIN' ? adminToken : accessToken);
  await withSessionLock(type, async () => {
    let res = await send('POST', authPath(type, 'logout'), { admin: type === 'ADMIN' });
    if (res.status === 401) {
      try {
        // 같은 Web Lock을 중첩 획득하지 않도록 여기서는 직접 재발급한다.
        const data = await parse(await send('POST', authPath(type, 'reissue')));
        const claims = readClaims(data.accessToken);
        if (claims?.type !== type || (original?.sid && claims.sid !== original.sid)) {
          loseSession(type);
          return;
        }
        if (type === 'ADMIN') setAdminToken(data.accessToken);
        else setAccessToken(data.accessToken);
        res = await send('POST', authPath(type, 'logout'), { admin: type === 'ADMIN' });
      } catch (e) {
        if (e.status !== 401 && !(e.status === 403 && e.code === 'AUTH-009')) throw e;
        loseSession(type);
        return;
      }
    }
    await parse(res);
    loseSession(type);
  });
  channel?.postMessage({ event: 'LOGOUT', type, sid: original?.sid });
}
export const logoutAdminSession = () => logoutSession('ADMIN');

async function request(method, path, options) {
  const type = isAdminPath(path) ? 'ADMIN' : 'MEMBER';
  let res = await send(method, path, options);
  const authEndpoint = path.startsWith('/api/auth/') || path.startsWith('/api/admin/auth/');
  if (res.status === 401 && !authEndpoint) {
    try {
      await reissueToken(type);
    } catch (e) {
      if (e.status === 401 || (e.status === 403 && e.code === 'AUTH-009')) loseSession(type);
      throw e;
    }
    res = await send(method, path, options);
    if (res.status === 401) loseSession(type);
  }
  return parse(res);
}

export const api = {
  get: (path, query) => request('GET', path, { query }),
  post: (path, body, headers) => request('POST', path, { body, headers }),
  put: (path, body) => request('PUT', path, { body }),
  patch: (path, body) => request('PATCH', path, { body }),
  delete: (path) => request('DELETE', path),
};
