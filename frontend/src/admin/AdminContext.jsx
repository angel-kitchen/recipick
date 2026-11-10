import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, getAdminSession, logoutAdminSession, restoreSession, setAdminAuthLostHandler, setAdminToken } from '../api/client';

// 관리자 토큰도 회원과 같이 메모리에만 둔다. 새로고침하면 리프레시 쿠키로 다시 받는다.
// 회원·관리자는 별도 Cookie·재발급 경로를 사용한다.
const AdminContext = createContext(null);

export function AdminProvider({ children }) {
  const [admin, setAdmin] = useState(() => getAdminSession());
  // 새로고침 직후 쿠키로 로그인 상태를 확인하는 동안은 로그인 화면으로 보내지 않는다
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let ignore = false;
    restoreSession("ADMIN").catch(() => null).finally(() => {
      if (ignore) return;
      setAdmin(getAdminSession());
      setLoading(false);
    });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    setAdminAuthLostHandler(() => setAdmin(null));
    return () => setAdminAuthLostHandler(() => {});
  }, []);

  const login = useCallback(async (loginId, password) => {
    const data = await api.post('/api/admin/auth/login', { loginId, password });
    const session = { adminId: data.adminId, role: data.role, martId: data.martId, loginId };
    setAdminToken(data.accessToken, session);
    setAdmin(session);
  }, []);

  const logout = useCallback(async () => {
    await logoutAdminSession();
    setAdmin(null);
  }, []);

  const value = useMemo(() => ({ admin, loading, login, logout }), [admin, loading, login, logout]);
  return <AdminContext.Provider value={value}>{children}</AdminContext.Provider>;
}

export function useAdmin() {
  return useContext(AdminContext);
}
