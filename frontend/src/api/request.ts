import axios, { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { TOKEN_KEY } from '../context/AuthContext';

// 扩展 Axios 配置类型以支持重试标记
declare module 'axios' {
  interface InternalAxiosRequestConfig {
    _retry?: boolean;
  }
}

/**
 * 后端统一响应结构
 */
interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
}

interface AuthResponse {
  token: string;
  refreshToken: string;
  userId: number;
  username: string;
}

const REFRESH_TOKEN_KEY = 'offermate_refresh_token';
const USERNAME_KEY = 'offermate_username';
const USER_ID_KEY = 'offermate_user_id';

const baseURL = import.meta.env.PROD ? '' : 'http://localhost:8080';

const instance: AxiosInstance = axios.create({
  baseURL,
  timeout: 60000,
});

/** 清除本地登录状态并跳转到登录页 */
function clearAuthAndRedirect(expired = false) {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
  localStorage.removeItem(USER_ID_KEY);
  window.location.href = expired ? '/login?expired=true' : '/login';
}

/** 并发 token-expired 请求队列 — 等待刷新完成后统一重试 */
let isRefreshing = false;
let failedQueue: Array<{ resolve: () => void; reject: (err: unknown) => void }> = [];

function processQueue(error: unknown) {
  failedQueue.forEach(item => (error ? item.reject(error) : item.resolve()));
  failedQueue = [];
}

/**
 * 请求拦截器 - 附加 Authorization Token
 */
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * 响应拦截器
 *
 * 后端约定：所有响应都是 HTTP 200 + Result
 * - code === 200  → 成功，返回 data
 * - code === 9007/9008/9009 → access token 已过期，尝试用 refresh token 刷新后重试
 * - code !== 200  → 业务失败，直接 reject 并显示 message
 */
instance.interceptors.response.use(
  async (response) => {
    const result = response.data as Result;

    if (result && typeof result === 'object' && 'code' in result) {
      if (result.code === 200) {
        response.data = result.data;
        return response;
      }

      // Token 已过期 → 尝试刷新（非认证接口、非已重试请求）
      if (
          (result.code === 9007 || result.code === 9008 || result.code === 9009) &&
        !response.config._retry &&
        !response.config.url?.includes('/api/auth/')
      ) {
        const originalRequest = response.config;
        const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);

        if (!refreshToken) {
          clearAuthAndRedirect(true);
          return Promise.reject(new Error('登录已过期，请重新登录'));
        }

        // 已有刷新请求在途 → 加入等待队列，刷新完成后重试
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({
              resolve: () => resolve(instance(originalRequest)),
              reject,
            });
          });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const refreshResponse = await instance.post(
            `/api/auth/refresh-token?refreshToken=${encodeURIComponent(refreshToken)}`
          );
          // 成功拦截器已将 data 解包为 AuthResponse
          const authData = refreshResponse.data as AuthResponse;

          localStorage.setItem(TOKEN_KEY, authData.token);
          localStorage.setItem(REFRESH_TOKEN_KEY, authData.refreshToken);
          localStorage.setItem(USERNAME_KEY, authData.username);
          localStorage.setItem(USER_ID_KEY, String(authData.userId));

          processQueue(null);
          return instance(originalRequest);
        } catch (refreshError) {
          processQueue(refreshError);
          clearAuthAndRedirect(true);
          return Promise.reject(new Error('登录已过期，请重新登录'));
        } finally {
          isRefreshing = false;
        }
      }

      return Promise.reject(new Error(result.message || '请求失败'));
    }

    return response;
  },
  (error) => {
    if (error.response) {
      const { data, status } = error.response;

      // HTTP 403：无 token 或 token 被 Spring Security 直接拒绝 → 登出
      if (status === 403) {
        clearAuthAndRedirect(false);
        return Promise.reject(new Error('请先登录'));
      }

      // 尝试解析 Result 格式的错误体
      if (data && typeof data === 'object' && 'code' in data && 'message' in data) {
        const result = data as Result;
        return Promise.reject(new Error(result.message || '请求失败'));
      }
      return Promise.reject(new Error('请求失败，请重试'));
    }

    // 没有响应：网络错误或连接中断
    const config = error.config;
    const isUpload = config && (
      config.url?.includes('/upload') ||
      config.headers?.['Content-Type']?.toString().includes('multipart')
    );

    if (isUpload) {
      return Promise.reject(new Error('上传失败，可能是网络超时或连接中断，请重试'));
    }

    return Promise.reject(new Error('网络连接失败，请检查网络'));
  }
);

export const request = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.get(url, config).then(res => res.data);
  },

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, data, config).then(res => res.data);
  },

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return instance.put(url, data, config).then(res => res.data);
  },

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return instance.delete(url, config).then(res => res.data);
  },

  upload<T>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
    return instance.post(url, formData, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
      ...config,
    }).then(res => res.data);
  },

  getInstance(): AxiosInstance {
    return instance;
  },
};

export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return '未知错误';
}

export default request;
