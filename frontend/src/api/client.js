// 액세스 토큰은 메모리에만 둔다. 새로고침하면 사라지지만 httpOnly 리프레시 쿠키로 다시 받는다.
let accessToken = null;
let reissuing = null;
let onAuthLost = () => {};
// 관리자 토큰은 회원 토큰과 따로 둔다. 관리자 API에는 재발급이 없어서 만료되면 다시 로그인해야 한다
let adminToken = null;
let onAdminAuthLost = () => {};
const isAdminPath = (path) => path.startsWith('/api/admin/');

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || '요청을 처리하지 못했습니다.');
    this.status = status;
    this.code = body?.code;
    this.errors = body?.errors ?? [];
  }
}

export function setAuthLostHandler(handler) {
  onAuthLost = handler;
}

export function setAdminToken(token) {
  adminToken = token;
}

export function setAdminAuthLostHandler(handler) {
  onAdminAuthLost = handler;
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

async function send(method, path, { body, headers, query } = {}) {
  const finalHeaders = { ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  const token = isAdminPath(path) ? adminToken : accessToken;
  if (token) finalHeaders.Authorization = `Bearer ${token}`;
  return fetch(buildUrl(path, query), {
    method,
    headers: finalHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  });
}

async function parse(res) {
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // 프록시가 돌려준 일반 텍스트 오류는 JSON이 아니다
  }
  // 백엔드가 꺼져 있으면 프록시가 본문 없는 502를 돌려준다
  if (!json && res.status >= 502) {
    throw new ApiError(res.status, { message: '서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해 주세요.' });
  }
  if (!res.ok || json?.success === false) throw new ApiError(res.status, json);
  return json?.data ?? null;
}

// 여러 요청이 동시에 401을 받아도 재발급은 한 번만 보낸다
export function reissueToken() {
  if (!reissuing) {
    reissuing = send('POST', '/api/auth/reissue')
      .then(parse)
      .then((data) => {
        accessToken = data.accessToken;
      })
      .catch((error) => {
        // 서버 오류·네트워크 오류는 로그인 상태와 무관하므로 토큰을 지우지 않는다
        if (error.status === 401 || error.status === 403) accessToken = null;
        throw error;
      })
      .finally(() => {
        reissuing = null;
      });
  }
  return reissuing;
}

export function setAccessToken(token) {
  accessToken = token;
}

async function request(method, path, options) {
  let res = await send(method, path, options);
  if (res.status === 401 && isAdminPath(path) && !path.startsWith('/api/admin/auth/')) {
    adminToken = null;
    onAdminAuthLost();
    throw new ApiError(401, { message: '관리자 로그인이 만료되었습니다. 다시 로그인해 주세요.' });
  }
  if (res.status === 401 && !isAdminPath(path) && !path.startsWith('/api/auth/')) {
    try {
      await reissueToken();
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) throw e;
      onAuthLost();
      throw new ApiError(401, { message: '로그인이 필요합니다. 다시 로그인해 주세요.' });
    }
    res = await send(method, path, options);
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
