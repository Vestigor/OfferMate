import { useState } from 'react';
import { X, Eye, EyeOff, CheckCircle, XCircle } from 'lucide-react';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/request';

interface ChangePasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const NEW_PASSWORD_RULES = [
  { label: '8-20 位字符', test: (p: string) => p.length >= 8 && p.length <= 20 },
  { label: '包含大写字母', test: (p: string) => /[A-Z]/.test(p) },
  { label: '包含小写字母', test: (p: string) => /[a-z]/.test(p) },
  { label: '包含数字', test: (p: string) => /\d/.test(p) },
  { label: '包含特殊字符 (@$!%*?&)', test: (p: string) => /[@$!%*?&]/.test(p) },
];

export default function ChangePasswordModal({ isOpen, onClose }: ChangePasswordModalProps) {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showOld, setShowOld] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [showRules, setShowRules] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const newPasswordValid = NEW_PASSWORD_RULES.every(r => r.test(newPassword));
  const confirmTouched = confirmPassword.length > 0;
  const passwordMatch = confirmTouched && newPassword === confirmPassword;

  const handleClose = () => {
    setOldPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setShowOld(false);
    setShowNew(false);
    setShowConfirm(false);
    setShowRules(false);
    setError('');
    setSuccess(false);
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!oldPassword || !newPasswordValid || !passwordMatch) return;

    setError('');
    setLoading(true);
    try {
      await authApi.changePassword(oldPassword, newPassword);
      setSuccess(true);
      setTimeout(handleClose, 1500);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40 dark:bg-black/60 backdrop-blur-sm"
        onClick={!loading ? handleClose : undefined}
      />

      {/* Dialog */}
      <div className="relative w-full max-w-sm bg-white dark:bg-slate-800 rounded-2xl shadow-2xl p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <h3 className="text-lg font-bold text-slate-800 dark:text-white">修改密码</h3>
          <button
            onClick={handleClose}
            disabled={loading}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors disabled:opacity-50"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {success ? (
          <div className="flex flex-col items-center gap-3 py-4">
            <CheckCircle className="w-12 h-12 text-emerald-500" />
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">密码修改成功</p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* 当前密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                当前密码
              </label>
              <div className="relative">
                <input
                  type={showOld ? 'text' : 'password'}
                  value={oldPassword}
                  onChange={e => setOldPassword(e.target.value)}
                  placeholder="请输入当前密码"
                  autoComplete="current-password"
                  className="w-full px-3.5 py-2.5 pr-11 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowOld(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showOld ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* 新密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                新密码
              </label>
              <div className="relative">
                <input
                  type={showNew ? 'text' : 'password'}
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  onFocus={() => setShowRules(true)}
                  placeholder="请输入新密码"
                  autoComplete="new-password"
                  className="w-full px-3.5 py-2.5 pr-11 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowNew(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                >
                  {showNew ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>

              {showRules && newPassword && (
                <div className="mt-2 p-3 rounded-xl bg-slate-50 dark:bg-slate-700/50 space-y-1.5">
                  {NEW_PASSWORD_RULES.map(rule => {
                    const ok = rule.test(newPassword);
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

            {/* 确认新密码 */}
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                确认新密码
              </label>
              <div className="relative">
                <input
                  type={showConfirm ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder="请再次输入新密码"
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
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
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

            <div className="flex gap-3 pt-1">
              <button
                type="button"
                onClick={handleClose}
                disabled={loading}
                className="flex-1 py-2.5 px-4 rounded-xl border border-slate-200 dark:border-slate-600 text-slate-700 dark:text-slate-300 font-medium hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors disabled:opacity-50"
              >
                取消
              </button>
              <button
                type="submit"
                disabled={loading || !oldPassword || !newPasswordValid || !passwordMatch}
                className="flex-1 py-2.5 px-4 bg-primary-600 hover:bg-primary-700 disabled:bg-primary-400 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-primary-500/25 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <span className="flex items-center justify-center gap-2">
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    修改中...
                  </span>
                ) : '确认修改'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
