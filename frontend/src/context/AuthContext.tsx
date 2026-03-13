import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

export const TOKEN_KEY = 'offermate_token';
export const REFRESH_TOKEN_KEY = 'offermate_refresh_token';
const USERNAME_KEY = 'offermate_username';
const USER_ID_KEY = 'offermate_user_id';

const BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

interface AuthUser {
  userId: number;
  username: string;
}

interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  login: (token: string, refreshToken: string, userId: number, username: string) => void;
  logout: () => void;
  updateUsername: (username: string) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    const username = localStorage.getItem(USERNAME_KEY);
    const userId = localStorage.getItem(USER_ID_KEY);
    const storedRefreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

    if (!token || !username || !userId) {
      setIsLoading(false);
      return;
    }

    const clearLocal = () => {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(USERNAME_KEY);
      localStorage.removeItem(USER_ID_KEY);
    };

    // 向后端验证 token 是否仍有效（页面刷新时调用）
    fetch(`${BASE_URL}/api/auth/validate`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(res => res.json())
      .then(async (result: { code: number }) => {
        if (result.code === 200) {
          // Token 有效
          setUser({ userId: Number(userId), username });
          setIsLoading(false);
          return;
        }

        if (result.code === 9008 && storedRefreshToken) {
          // Access token 过期，但有 refresh token → 尝试续期
          try {
            const refreshRes = await fetch(
              `${BASE_URL}/api/auth/refresh-token?refreshToken=${encodeURIComponent(storedRefreshToken)}`,
              { method: 'POST' }
            );
            const refreshResult = await refreshRes.json() as {
              code: number;
              data: { token: string; refreshToken: string; userId: number; username: string };
            };

            if (refreshResult.code === 200) {
              const d = refreshResult.data;
              localStorage.setItem(TOKEN_KEY, d.token);
              localStorage.setItem(REFRESH_TOKEN_KEY, d.refreshToken);
              localStorage.setItem(USERNAME_KEY, d.username);
              localStorage.setItem(USER_ID_KEY, String(d.userId));
              setUser({ userId: d.userId, username: d.username });
              setIsLoading(false);
            } else {
              // refresh token 也已过期
              clearLocal();
              window.location.replace('/login?expired=true');
            }
          } catch {
            // 刷新请求网络失败，保留用户状态避免中断体验
            setUser({ userId: Number(userId), username });
            setIsLoading(false);
          }
          return;
        }

        // token 无效（篡改、黑名单等），直接清除，ProtectedRoute 负责跳转
        clearLocal();
        setIsLoading(false);
      })
      .catch(() => {
        // 网络错误时不强制登出，保留状态，让后续请求的拦截器处理
        setUser({ userId: Number(userId), username });
        setIsLoading(false);
      });
  }, []);

  const login = (token: string, refreshToken: string, userId: number, username: string) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    localStorage.setItem(USERNAME_KEY, username);
    localStorage.setItem(USER_ID_KEY, String(userId));
    setUser({ userId, username });
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
    localStorage.removeItem(USER_ID_KEY);
    setUser(null);
  };

  const updateUsername = (username: string) => {
    localStorage.setItem(USERNAME_KEY, username);
    setUser(prev => prev ? { ...prev, username } : null);
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout, updateUsername }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
