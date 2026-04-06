import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Sparkles, Eye, EyeOff, CheckCircle, XCircle } from 'lucide-react';
import { authApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { getErrorMessage } from '../api/request';

interface PasswordRule {
  label: string;
  test: (p: string) => boolean;
}

const PASSWORD_RULES: PasswordRule[] = [
  { label: '8-20 位字符', test: p => p.length >= 8 && p.length <= 20 },
  { label: '包含大写字母', test: p => /[A-Z]/.test(p) },
  { label: '包含小写字母', test: p => /[a-z]/.test(p) },
  { label: '包含数字', test: p => /\d/.test(p) },
  { label: '包含特殊字符 (@$!%*?&)', test: p => /[@$!%*?&]/.test(p) },
];

export default function RegisterPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [showRules, setShowRules] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const passwordValid = PASSWORD_RULES.every(r => r.test(password));
  const usernameValid = /^[a-zA-Z][a-zA-Z0-9_]{2,19}$/.test(username);
  const passwordMatch = confirmPassword.length > 0 && password === confirmPassword;
  const confirmTouched = confirmPassword.length > 0;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!usernameValid || !passwordValid || !passwordMatch) return;

    setError('');
    setLoading(true);
    try {
      const res = await authApi.register(username.trim(), password);
      login(res.token, res.refreshToken, res.userId, res.username);
      navigate('/upload', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <span className="text-2xl font-bold text-slate-800 dark:text-white tracking-tight block">Offer Mate</span>
            <span className="text-sm text-slate-400 dark:text-slate-500">你的 AI 求职助手</span>
          </div>
        </div>

        {/* Card */}
        <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-xl shadow-slate-200/60 dark:shadow-slate-900/60 p-8">
          <h1 className="text-xl font-bold text-slate-800 dark:text-white mb-2">创建账号</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400 mb-6">注册以开始使用 AI 面试助手</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* 用户名 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                用户名
              </label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="以字母开头，可含字母、数字、下划线"
                autoComplete="username"
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
              />
              {username && !usernameValid && (
                <p className="mt-1.5 text-xs text-red-500 dark:text-red-400">
                  用户名需以字母开头，3-20 位，只能含字母、数字和下划线
                </p>
              )}
            </div>

            {/* 密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                密码
              </label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  onFocus={() => setShowRules(true)}
                  placeholder="请设置密码"
                  autoComplete="new-password"
                  className="w-full px-3.5 py-2.5 pr-11 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>

              {/* 密码强度规则 */}
              {showRules && password && (
                <div className="mt-2 p-3 rounded-xl bg-slate-50 dark:bg-slate-700/50 space-y-1.5">
                  {PASSWORD_RULES.map(rule => {
                    const ok = rule.test(password);
                    return (
                      <div key={rule.label} className="flex items-center gap-2">
                        {ok
                          ? <CheckCircle className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                          : <XCircle className="w-3.5 h-3.5 text-slate-300 dark:text-slate-600 shrink-0" />
                        }
                        <span className={`text-xs ${ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-400 dark:text-slate-500'}`}>
                          {rule.label}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 确认密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                确认密码
              </label>
              <div className="relative">
                <input
                  type={showConfirm ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入密码"
                  autoComplete="new-password"
                  className={`w-full px-3.5 py-2.5 pr-11 rounded-xl border bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:border-transparent transition-all
                    ${confirmTouched && !passwordMatch
                      ? 'border-red-300 dark:border-red-600 focus:ring-red-400'
                      : 'border-slate-200 dark:border-slate-600 focus:ring-primary-500'
                    }`}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showConfirm ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {confirmTouched && !passwordMatch && (
                <p className="mt-1.5 text-xs text-red-500 dark:text-red-400">两次输入的密码不一致</p>
              )}
              {confirmTouched && passwordMatch && (
                <p className="mt-1.5 text-xs text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                  <CheckCircle className="w-3.5 h-3.5" />
                  密码一致
                </p>
              )}
            </div>

            {error && (
              <div className="px-3.5 py-2.5 rounded-xl bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800">
                <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !usernameValid || !passwordValid || !passwordMatch}
              className="w-full py-2.5 px-4 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-400 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-primary-500/25 disabled:cursor-not-allowed"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  注册中...
                </span>
              ) : '注册'}
            </button>
          </form>

          <p className="text-center text-sm text-slate-500 dark:text-slate-400 mt-6">
            已有账号？{' '}
            <Link to="/login" className="text-primary-600 dark:text-primary-400 font-medium hover:underline">
              立即登录
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
