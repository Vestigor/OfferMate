import { request } from './request';

export interface AuthResponse {
  token: string;
  refreshToken: string;
  userId: number;
  username: string;
}

export const authApi = {
  login(username: string, password: string) {
    return request.post<AuthResponse>('/api/auth/login', { username, password });
  },

  register(username: string, password: string) {
    return request.post<AuthResponse>('/api/auth/register', { username, password });
  },

  logout() {
    return request.post<null>('/api/auth/logout');
  },

  changePassword(oldPassword: string, newPassword: string) {
    return request.put<null>('/api/auth/password', { oldPassword, newPassword });
  },

  deleteAccount() {
    return request.delete<null>('/api/auth/account');
  },
};
