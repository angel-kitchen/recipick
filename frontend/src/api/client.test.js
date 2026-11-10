import test from 'node:test';
import assert from 'node:assert/strict';

globalThis.BroadcastChannel = class { postMessage() {} };
Object.defineProperty(globalThis, 'navigator', {
  value: { locks: { request: async (_name, work) => work() } }, configurable: true,
});
const token = (type, sid = 'session-a') =>
  `x.${Buffer.from(JSON.stringify({ type, sid, adminId: 1, role: 'SUPER_ADMIN' })).toString('base64url')}.x`;
const reply = (data = null, status = 200, code) => new Response(
  JSON.stringify({ success: status < 400, data, code }), { status, headers: { 'Content-Type': 'application/json' } });
async function client() { return import(`./client.js?case=${Math.random()}`); }

test('회원·관리자 재발급은 각 경로로 보내고 같은 주체의 동시 호출은 한 번만 보낸다', async () => {
  const api = await client();
  const calls = [];
  globalThis.fetch = async (path) => {
    calls.push(path);
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'protection' });
    await new Promise((resolve) => setTimeout(resolve, 5));
    return reply({ accessToken: token(path.includes('/admin/') ? 'ADMIN' : 'MEMBER') });
  };
  assert.deepEqual(await Promise.all([api.reissueToken(), api.reissueToken(), api.reissueToken('ADMIN')]),
    ['MEMBER', 'MEMBER', 'ADMIN']);
  assert.equal(calls.filter((p) => p === '/api/auth/reissue').length, 1);
  assert.equal(calls.filter((p) => p === '/api/admin/auth/reissue').length, 1);
});

test('인증 Cookie를 변경하는 요청은 CSRF 헤더를 보내고 명확한 CSRF 거절만 한 번 재시도한다', async () => {
  const { api } = await client();
  let attempts = 0;
  globalThis.fetch = async (path, options) => {
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'protection' });
    assert.equal(options.headers['X-CSRF-TOKEN'], 'protection');
    attempts++;
    return attempts === 1 ? reply(null, 403, 'AUTH-013') : reply({ accessToken: token('MEMBER') });
  };
  await api.post('/api/auth/login', { email: 'a', password: 'b' });
  assert.equal(attempts, 2);
});

test('서버 로그아웃이 실패하면 회원 인증 상태를 성공처럼 지우지 않는다', async () => {
  const api = await client();
  let lost = 0;
  api.setAccessToken(token('MEMBER'));
  api.setAuthLostHandler(() => lost++);
  globalThis.fetch = async (path, options) => {
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'protection' });
    if (path === '/api/auth/logout') return reply(null, 500, 'COMMON-500');
    assert.equal(options.headers.Authorization, `Bearer ${token('MEMBER')}`);
    return reply({ memberId: 1 });
  };
  await assert.rejects(api.logoutSession(), (e) => e.status === 500);
  assert.equal(lost, 0);
  assert.deepEqual(await api.api.get('/api/members/me'), { memberId: 1 });
});

test('재발급 저장소 장애를 세션 만료로 오인해 로그아웃하지 않는다', async () => {
  const api = await client();
  let lost = 0;
  api.setAccessToken(token('MEMBER'));
  api.setAuthLostHandler(() => lost++);
  globalThis.fetch = async (path) => {
    if (path === '/api/auth/csrf') return reply({ headerName: 'X-CSRF-TOKEN', token: 'protection' });
    if (path === '/api/auth/reissue') return reply(null, 500, 'COMMON-500');
    return reply(null, 401, 'AUTH-003');
  };
  await assert.rejects(api.api.get('/api/members/me'), (e) => e.status === 500);
  assert.equal(lost, 0);
});

test('관리자 로그아웃은 관리자 경로만 호출한다', async () => {
  const api = await client();
  api.setAdminToken(token('ADMIN'));
  const calls = [];
  globalThis.fetch = async (path) => {
    calls.push(path);
    return path === '/api/auth/csrf'
      ? reply({ headerName: 'X-CSRF-TOKEN', token: 'protection' }) : new Response(null, { status: 204 });
  };
  await api.logoutAdminSession();
  assert.ok(calls.includes('/api/admin/auth/logout'));
  assert.ok(!calls.includes('/api/auth/logout'));
  assert.equal(api.getAdminSession(), null);
});
