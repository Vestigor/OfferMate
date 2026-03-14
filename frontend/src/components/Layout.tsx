import { useState, useRef } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ChevronRight, Database, FileStack, Key, LogOut, MessageSquare, Moon, Sparkles, Sun, Trash2, Upload, User, Users } from 'lucide-react';
import { useTheme } from '../hooks/useTheme';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../api/auth';
import ConfirmModal from './ConfirmModal';
import ChangePasswordModal from './ChangePasswordModal';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const currentPath = location.pathname;
  const { theme, toggleTheme } = useTheme();
  const { user, logout } = useAuth();

  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const hideMenuTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const navGroups: NavGroup[] = [
    {
      id: 'career',
      title: '简历与面试',
      items: [
        { id: 'upload', path: '/upload', label: '上传简历', icon: Upload, description: 'AI 分析简历' },
        { id: 'resumes', path: '/history', label: '简历库', icon: FileStack, description: '管理所有简历' },
        { id: 'interviews', path: '/interviews', label: '面试记录', icon: Users, description: '查看面试历史' },
      ],
    },
    {
      id: 'knowledge',
      title: '知识库',
      items: [
        { id: 'kb-manage', path: '/knowledgebase', label: '知识库管理', icon: Database, description: '管理知识文档' },
        { id: 'chat', path: '/knowledgebase/chat', label: '问答助手', icon: MessageSquare, description: '基于知识库问答' },
      ],
    },
  ];

  const isActive = (path: string) => {
    if (path === '/upload') {
      return currentPath === '/upload' || currentPath === '/';
    }
    if (path === '/knowledgebase') {
      return currentPath === '/knowledgebase' || currentPath === '/knowledgebase/upload';
    }
    return currentPath.startsWith(path);
  };

  const handleMouseEnterUser = () => {
    if (hideMenuTimer.current) clearTimeout(hideMenuTimer.current);
    setShowUserMenu(true);
  };

  const handleMouseLeaveUser = () => {
    hideMenuTimer.current = setTimeout(() => setShowUserMenu(false), 150);
  };

  const handleLogoutConfirm = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore — clear local state regardless
    }
    logout();
    navigate('/login', { replace: true });
  };

  const handleDeleteConfirm = async () => {
    setDeleteLoading(true);
    try {
      await authApi.deleteAccount();
      logout();
      navigate('/login', { replace: true });
    } catch {
      setDeleteLoading(false);
    }
  };

  // User avatar: first letter of username
  const avatarLetter = user?.username?.[0]?.toUpperCase() ?? '?';

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800">
      {/* 左侧边栏 */}
      <aside className="w-64 bg-white dark:bg-slate-900 border-r border-slate-100 dark:border-slate-700 fixed h-screen left-0 top-0 z-50 flex flex-col">
        {/* Logo */}
        <div className="p-6 border-b border-slate-100 dark:border-slate-700 flex items-center justify-between">
          <Link to="/upload" className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <span className="text-lg font-bold text-slate-800 dark:text-white tracking-tight block">Offer Mate</span>
              <span className="text-xs text-slate-400 dark:text-slate-500">AI 求职助手</span>
            </div>
          </Link>
        </div>

        {/* 主题切换按钮 */}
        <div className="px-4 pb-2">
          <button
            onClick={toggleTheme}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
          >
            {theme === 'dark' ? (
              <>
                <Sun className="w-4 h-4" />
                <span className="text-sm font-medium">浅色模式</span>
              </>
            ) : (
              <>
                <Moon className="w-4 h-4" />
                <span className="text-sm font-medium">深色模式</span>
              </>
            )}
          </button>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-6">
            {navGroups.map((group) => (
              <div key={group.id}>
                <div className="px-3 mb-2">
                  <span className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
                            : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white'
                          }`}
                      >
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-primary-100 dark:bg-primary-900/50 text-primary-600 dark:text-primary-400'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 group-hover:bg-slate-200 dark:group-hover:bg-slate-700 group-hover:text-slate-700 dark:group-hover:text-white'
                          }`}
                        >
                          <item.icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-slate-400 dark:text-slate-500 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-primary-400" />
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        {/* 底部用户区域 */}
        <div
          className="relative p-4 border-t border-slate-100 dark:border-slate-700"
          onMouseEnter={handleMouseEnterUser}
          onMouseLeave={handleMouseLeaveUser}
        >
          {/* 用户操作菜单（向上弹出） */}
          {showUserMenu && (
            <div className="absolute bottom-full left-4 right-4 mb-2 bg-white dark:bg-slate-800 rounded-xl shadow-xl shadow-slate-200/60 dark:shadow-slate-900/60 border border-slate-100 dark:border-slate-700 overflow-hidden">
              <button
                onClick={() => { setShowUserMenu(false); setShowChangePassword(true); }}
                className="w-full flex items-center gap-3 px-4 py-3 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
              >
                <Key className="w-4 h-4 text-slate-400 dark:text-slate-500" />
                修改密码
              </button>
              <div className="h-px bg-slate-100 dark:bg-slate-700" />
              <button
                onClick={() => { setShowUserMenu(false); setShowLogoutConfirm(true); }}
                className="w-full flex items-center gap-3 px-4 py-3 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
              >
                <LogOut className="w-4 h-4 text-slate-400 dark:text-slate-500" />
                退出登录
              </button>
              <div className="h-px bg-slate-100 dark:bg-slate-700" />
              <button
                onClick={() => { setShowUserMenu(false); setShowDeleteConfirm(true); }}
                className="w-full flex items-center gap-3 px-4 py-3 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
              >
                <Trash2 className="w-4 h-4" />
                注销账号
              </button>
            </div>
          )}

          {/* 用户信息 */}
          <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-800 cursor-default hover:bg-slate-100 dark:hover:bg-slate-700/80 transition-colors">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white text-sm font-bold shrink-0 shadow-sm">
              {avatarLetter}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-slate-800 dark:text-white truncate">
                {user?.username ?? '用户'}
              </p>
              <p className="text-xs text-slate-400 dark:text-slate-500">悬停查看选项</p>
            </div>
            <User className="w-4 h-4 text-slate-300 dark:text-slate-600 shrink-0" />
          </div>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 p-10 min-h-screen overflow-y-auto">
        <motion.div
          key={currentPath}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Outlet />
        </motion.div>
      </main>

      {/* 修改密码弹窗 */}
      <ChangePasswordModal
        isOpen={showChangePassword}
        onClose={() => setShowChangePassword(false)}
      />

      {/* 退出登录确认弹窗 */}
      <ConfirmModal
        isOpen={showLogoutConfirm}
        title="退出登录"
        description="确定要退出当前账号吗？"
        confirmText="退出登录"
        onConfirm={handleLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
      />

      {/* 注销账号确认弹窗 */}
      <ConfirmModal
        isOpen={showDeleteConfirm}
        title="注销账号"
        description="注销后所有数据将被永久删除且无法恢复，确定要继续吗？"
        confirmText="确认注销"
        variant="danger"
        isLoading={deleteLoading}
        onConfirm={handleDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
      />
    </div>
  );
}
