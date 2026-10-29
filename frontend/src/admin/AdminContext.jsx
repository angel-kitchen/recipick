import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setAdminAuthLostHandler, setAdminToken } from '../api/client';

// 관리자 API에는 재발급이 없어서 토큰을 탭 단위 저장소(sessionStorage)에 두고 새로고침을 견디게 한다.
// 탭을 닫으면 사라진다.
const KEY = 'recipick.admin';

function readStored() {
  try {
    return JSON.parse(sessionStorage.getItem(KEY));
  } catch {
    return null;
  }
}

function writeStored(value) {
  try {
    if (value) sessionStorage.setItem(KEY, JSON.stringify(value));
    else sessionStorage.removeItem(KEY);
  } catch {
    // 저장소를 못 쓰면 새로고침 때 다시 로그인하면 된다
  }
}

const AdminContext = createContext(null);

export function AdminProvider({ children }) {
  const [admin, setAdmin] = useState(() => {
    const stored = readStored();
    if (stored?.token) setAdminToken(stored.token);
    return stored?.token ? stored : null;
  });

  const clear = useCallback(() => {
    setAdminToken(null);
    writeStored(null);
    setAdmin(null);
  }, []);

  useEffect(() => {
    setAdminAuthLostHandler(clear);
    return () => setAdminAuthLostHandler(() => {});
  }, [clear]);

  const login = useCallback(async (loginId, password) => {
    const data = await api.post('/api/admin/auth/login', { loginId, password });
    setAdminToken(data.accessToken);
    const next = { token: data.accessToken, adminId: data.adminId, role: data.role, martId: data.martId, loginId };
    writeStored(next);
    setAdmin(next);
  }, []);

  const value = useMemo(() => ({ admin, login, logout: clear }), [admin, login, clear]);
  return <AdminContext.Provider value={value}>{children}</AdminContext.Provider>;
}

export function useAdmin() {
  return useContext(AdminContext);
}
