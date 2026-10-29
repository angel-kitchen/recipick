// 액세스 토큰은 회원·관리자 모두 메모리에만 둔다. 새로고침하면 사라지지만 httpOnly 리프레시 쿠키로 다시 받는다.
let accessToken = null;
let onAuthLost = () => {};
// 회원과 관리자는 같은 리프레시 쿠키를 쓰므로 한 브라우저에서는 나중에 로그인한 쪽이 쿠키를 차지한다.
// 액세스 토큰은 둘을 따로 들고 있다가 경로(/api/admin/ 여부)에 맞는 쪽을 붙인다.
let adminToken = null;
let adminSession = null;
let onAdminAuthLost = () => {};
let reissuing = null;
let restoring = null;
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

export function setAdminAuthLostHandler(handler) {
  onAdminAuthLost = handler;
}

// 토큰 본문(payload)만 읽는다. 서명 검증은 서버가 하고, 여기서는 누구의 토큰인지 화면에 쓰려는 용도다
function readClaims(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

export function setAccessToken(token) {
  accessToken = token;
}

export function setAdminToken(token, session = null) {
  adminToken = token;
  adminSession = token ? session ?? adminSessionFrom(token) : null;
}

function adminSessionFrom(token) {
  const claims = readClaims(token);
  return claims ? { adminId: claims.adminId, role: claims.role, martId: claims.martId ?? null } : null;
}

export function getAdminSession() {
  return adminSession;
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

async function send(method, path, { body, headers, query, admin } = {}) {
  const finalHeaders = { ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  const token = admin || isAdminPath(path) ? adminToken : accessToken;
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

// 쿠키 하나로 회원·관리자 중 한쪽만 재발급되므로, 받은 토큰이 누구 것인지 보고 알맞은 자리에 둔다.
// 서버가 리프레시 토큰을 유예 없이 교체하기 때문에 동시에 여러 번 보내면 하나만 성공한다.
// 그래서 여러 요청이 동시에 401을 받아도 재발급은 한 번만 보낸다.
// 돌려주는 값은 'MEMBER' 또는 'ADMIN'이다.
export function reissueToken() {
  if (!reissuing) {
    reissuing = send('POST', '/api/auth/reissue')
      .then(parse)
      .then((data) => {
        const claims = readClaims(data.accessToken);
        if (claims?.type === 'ADMIN') {
          setAdminToken(data.accessToken);
          return 'ADMIN';
        }
        accessToken = data.accessToken;
        return 'MEMBER';
      })
      .catch((error) => {
        // 서버 오류·네트워크 오류는 로그인 상태와 무관하므로 토큰을 지우지 않는다
        if (error.status === 401 || error.status === 403) {
          accessToken = null;
          setAdminToken(null);
        }
        throw error;
      })
      .finally(() => {
        reissuing = null;
      });
  }
  return reissuing;
}

// 페이지를 연 직후 한 번만 재발급해서 로그인 상태를 복구한다. 회원·관리자 화면이 같은 결과를 나눠 쓴다.
// 로그인한 적이 없어 실패하면 null이다.
export function restoreSession() {
  if (!restoring) restoring = reissueToken().catch(() => null);
  return restoring;
}

// 관리자 로그아웃: 서버의 리프레시 세션을 지워야 새로고침해도 다시 로그인되지 않는다
export async function logoutAdminSession() {
  try {
    let res = await send('POST', '/api/auth/logout', { admin: true });
    if (res.status === 401 && (await reissueToken().catch(() => null)) === 'ADMIN') {
      res = await send('POST', '/api/auth/logout', { admin: true });
    }
  } catch {
    // 연결이 안 돼도 이 화면에서는 로그아웃 상태로 만든다
  } finally {
    setAdminToken(null);
  }
}

async function request(method, path, options) {
  const admin = isAdminPath(path);
  let res = await send(method, path, options);
  const reissuable = res.status === 401 && !path.startsWith('/api/auth/') && !path.startsWith('/api/admin/auth/');
  if (reissuable) {
    let who = null;
    try {
      who = await reissueToken();
    } catch (e) {
      if (e.status !== 401 && e.status !== 403) throw e;
    }
    // 재발급된 토큰이 이 요청의 주인(회원/관리자)과 같아야 다시 시도한다
    if (who === (admin ? 'ADMIN' : 'MEMBER')) {
      res = await send(method, path, options);
    } else if (admin) {
      setAdminToken(null);
      onAdminAuthLost();
      throw new ApiError(401, { message: '관리자 로그인이 만료되었습니다. 다시 로그인해 주세요.' });
    } else {
      onAuthLost();
      throw new ApiError(401, { message: '로그인이 필요합니다. 다시 로그인해 주세요.' });
    }
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
