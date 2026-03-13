import { AlertTriangle } from 'lucide-react';

interface ConfirmModalProps {
  isOpen: boolean;
  title: string;
  description?: string;
  confirmText?: string;
  cancelText?: string;
  variant?: 'default' | 'danger';
  isLoading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export default function ConfirmModal({
  isOpen,
  title,
  description,
  confirmText = '确认',
  cancelText = '取消',
  variant = 'default',
  isLoading = false,
  onConfirm,
  onClose,
}: ConfirmModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40 dark:bg-black/60 backdrop-blur-sm"
        onClick={!isLoading ? onClose : undefined}
      />

      {/* Dialog */}
      <div className="relative w-full max-w-sm bg-white dark:bg-slate-800 rounded-2xl shadow-2xl p-6">
        {variant === 'danger' && (
          <div className="flex items-center justify-center w-12 h-12 rounded-full bg-red-100 dark:bg-red-900/30 mx-auto mb-4">
            <AlertTriangle className="w-6 h-6 text-red-600 dark:text-red-400" />
          </div>
        )}

        <h3 className="text-lg font-bold text-slate-800 dark:text-white text-center mb-2">
          {title}
        </h3>

        {description && (
          <p className="text-sm text-slate-500 dark:text-slate-400 text-center mb-6">
            {description}
          </p>
        )}

        {!description && <div className="mb-6" />}

        <div className="flex gap-3">
          <button
            onClick={onClose}
            disabled={isLoading}
            className="flex-1 py-2.5 px-4 rounded-xl border border-slate-200 dark:border-slate-600 text-slate-700 dark:text-slate-300 font-medium hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {cancelText}
          </button>
          <button
            onClick={onConfirm}
            disabled={isLoading}
            className={`flex-1 py-2.5 px-4 rounded-xl font-semibold transition-colors disabled:cursor-not-allowed
              ${variant === 'danger'
                ? 'bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white shadow-lg shadow-red-500/25'
                : 'bg-primary-600 hover:bg-primary-700 disabled:bg-primary-400 text-white shadow-lg shadow-primary-500/25'
              }`}
          >
            {isLoading ? (
              <span className="flex items-center justify-center gap-2">
                <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                处理中...
              </span>
            ) : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
