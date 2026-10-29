import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, reissueToken, setAccessToken, setAuthLostHandler } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [member, setMember] = useState(null);
  // 새로고침 직후에는 쿠키로 로그인 상태를 확인하는 동안 화면을 잠시 기다린다
  const [loading, setLoading] = useState(true);

  const loadMember = useCallback(async () => {
    setMember(await api.get('/api/members/me'));
  }, []);

  useEffect(() => {
    setAuthLostHandler(() => setMember(null));
    reissueToken()
      .then(loadMember)
      .catch(() => setMember(null))
      .finally(() => setLoading(false));
  }, [loadMember]);

  const login = useCallback(
    async (email, password) => {
      const data = await api.post('/api/auth/login', { email, password });
      setAccessToken(data.accessToken);
      await loadMember();
    },
    [loadMember],
  );

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout');
    } finally {
      setAccessToken(null);
      setMember(null);
    }
  }, []);

  const value = useMemo(
    () => ({ member, loading, login, logout, reloadMember: loadMember }),
    [member, loading, login, logout, loadMember],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
