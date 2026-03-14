import {useCallback, useEffect, useRef, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {EvaluateStatus, historyApi, InterviewItem} from '../api/history';
import {formatDate} from '../utils/date';
import DeleteConfirmDialog from '../components/DeleteConfirmDialog';
import {
  AlertCircle,
  CheckCircle,
  ChevronRight,
  Clock,
  Download,
  FileText,
  Loader2,
  PlayCircle,
  RefreshCw,
  Search,
  Trash2,
  TrendingUp,
  Users,
} from 'lucide-react';

interface InterviewHistoryPageProps {
  onBack: () => void;
  onViewInterview: (sessionId: string, resumeId?: number) => void;
  onContinueInterview?: (resumeId: number) => void;
}

interface InterviewWithResume extends InterviewItem {
  resumeId: number;
  resumeFilename: string;
  evaluateStatus?: EvaluateStatus;
  evaluateError?: string;
}

interface InterviewStats {
  totalCount: number;
  completedCount: number;
  averageScore: number;
}

// 统计卡片组件
function StatCard({
  icon: Icon,
  label,
  value,
  suffix,
  color,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number | string;
  suffix?: string;
  color: string;
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700"
    >
      <div className="flex items-center gap-4">
        <div className={`p-3 rounded-lg ${color}`}>
          <Icon className="w-6 h-6 text-white" />
        </div>
        <div>
            <p className="text-sm text-slate-500 dark:text-slate-400">{label}</p>
            <p className="text-2xl font-bold text-slate-800 dark:text-white">
                {value}{suffix &&
                <span className="text-base font-normal text-slate-400 dark:text-slate-500 ml-1">{suffix}</span>}
          </p>
        </div>
      </div>
    </motion.div>
  );
}

// 判断是否为生成中状态
function isGeneratingStatus(status: string): boolean {
  return status === 'GENERATING';
}

// 判断是否为已完成状态（包括 COMPLETED 和 EVALUATED）
function isCompletedStatus(status: string): boolean {
  return status === 'COMPLETED' || status === 'EVALUATED';
}

// 判断评估是否完成
function isEvaluateCompleted(interview: InterviewWithResume): boolean {
  // 如果 evaluateStatus 存在且为 COMPLETED，则评估已完成
  if (interview.evaluateStatus === 'COMPLETED') return true;
  // 向后兼容：如果 status 为 EVALUATED，也认为评估已完成
  if (interview.status === 'EVALUATED') return true;
  return false;
}

// 判断是否正在评估中
function isEvaluating(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'PENDING' || interview.evaluateStatus === 'PROCESSING';
}

// 判断评估是否失败
function isEvaluateFailed(interview: InterviewWithResume): boolean {
  return interview.evaluateStatus === 'FAILED';
}

// 状态图标
function StatusIcon({ interview }: { interview: InterviewWithResume }) {
  // 题目生成中
  if (isGeneratingStatus(interview.status)) {
      return <Loader2 className="w-4 h-4 text-blue-500 dark:text-blue-400 animate-spin"/>;
  }
  // 评估失败
  if (isEvaluateFailed(interview)) {
      return <AlertCircle className="w-4 h-4 text-red-500 dark:text-red-400"/>;
  }
  // 正在评估
  if (isEvaluating(interview)) {
      return <RefreshCw className="w-4 h-4 text-blue-500 dark:text-blue-400 animate-spin"/>;
  }
  // 评估完成
  if (isEvaluateCompleted(interview)) {
      return <CheckCircle className="w-4 h-4 text-green-500 dark:text-green-400"/>;
  }
  // 面试进行中
  if (interview.status === 'IN_PROGRESS') {
      return <PlayCircle className="w-4 h-4 text-blue-500 dark:text-blue-400"/>;
  }
  // 面试已完成但评估未开始
  if (isCompletedStatus(interview.status)) {
      return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
  }
  // 已创建（待开始）
    return <Clock className="w-4 h-4 text-yellow-500 dark:text-yellow-400"/>;
}

// 状态文本
function getStatusText(interview: InterviewWithResume): string {
  // 题目生成中
  if (isGeneratingStatus(interview.status)) {
    return '生成中';
  }
  // 评估失败
  if (isEvaluateFailed(interview)) {
    return '评估失败';
  }
  // 正在评估
  if (isEvaluating(interview)) {
    return interview.evaluateStatus === 'PROCESSING' ? '评估中' : '等待评估';
  }
  // 评估完成
  if (isEvaluateCompleted(interview)) {
    return '已完成';
  }
  // 面试进行中
  if (interview.status === 'IN_PROGRESS') {
    return '进行中';
  }
  // 面试已完成但评估未开始
  if (isCompletedStatus(interview.status)) {
    return '已提交';
  }
  return '已创建';
}

// 获取分数颜色
function getScoreColor(score: number): string {
  if (score >= 80) return 'bg-green-500';
  if (score >= 60) return 'bg-yellow-500';
  return 'bg-red-500';
}

type ModalType = 'generating' | 'ready';

export default function InterviewHistoryPage({ onBack: _onBack, onViewInterview, onContinueInterview }: InterviewHistoryPageProps) {
  const [interviews, setInterviews] = useState<InterviewWithResume[]>([]);
  const [stats, setStats] = useState<InterviewStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteItem, setDeleteItem] = useState<InterviewWithResume | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const [statusModal, setStatusModal] = useState<{ type: ModalType; interview: InterviewWithResume } | null>(null);
  const pollingRef = useRef<number | null>(null);

  const loadAllInterviews = useCallback(async (isPolling = false) => {
    if (!isPolling) {
      setLoading(true);
    }
    try {
      const resumes = await historyApi.getResumes();
      const allInterviews: InterviewWithResume[] = [];

      for (const resume of resumes) {
        const detail = await historyApi.getResumeDetail(resume.id);
        if (detail.interviews && detail.interviews.length > 0) {
          detail.interviews.forEach(interview => {
            allInterviews.push({
              ...interview,
              resumeId: resume.id,
              resumeFilename: resume.filename
            });
          });
        }
      }

      // 按创建时间倒序排序
      allInterviews.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );

      setInterviews(allInterviews);

      // 计算统计信息（只统计评估已完成的面试）
      const evaluated = allInterviews.filter(i => isEvaluateCompleted(i));
      const totalScore = evaluated.reduce((sum, i) => sum + (i.overallScore || 0), 0);
      setStats({
        totalCount: allInterviews.length,
        completedCount: evaluated.length,
        averageScore: evaluated.length > 0 ? Math.round(totalScore / evaluated.length) : 0,
      });
    } catch (err) {
      console.error('加载面试记录失败', err);
    } finally {
      if (!isPolling) {
        setLoading(false);
      }
    }
  }, []);

  // 初始加载
  useEffect(() => {
    loadAllInterviews();
  }, [loadAllInterviews]);

  // 轮询检查评估状态和题目生成状态
  useEffect(() => {
    // 检查是否有正在评估或正在生成题目的面试
    const hasEvaluating = interviews.some(i => isEvaluating(i) || isGeneratingStatus(i.status));

    if (hasEvaluating) {
      // 启动轮询
      pollingRef.current = window.setInterval(() => {
        loadAllInterviews(true);
      }, 3000); // 每3秒轮询一次
    } else {
      // 停止轮询
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    }

    return () => {
      if (pollingRef.current) {
        clearInterval(pollingRef.current);
        pollingRef.current = null;
      }
    };
  }, [interviews, loadAllInterviews]);

  const handleDeleteClick = (interview: InterviewWithResume, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteItem(interview);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteItem) return;

    setDeletingSessionId(deleteItem.sessionId);
    try {
      await historyApi.deleteInterview(deleteItem.sessionId);
      await loadAllInterviews();
      setDeleteItem(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleExport = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExporting(sessionId);
    try {
      const blob = await historyApi.exportInterviewPdf(sessionId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `面试报告_${sessionId.slice(-8)}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert('导出失败，请重试');
    } finally {
      setExporting(null);
    }
  };

  const filteredInterviews = interviews.filter(interview =>
    interview.resumeFilename.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div
      className="w-full"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      {/* 头部 */}
      <div className="flex justify-between items-start mb-8 flex-wrap gap-6">
        <div>
          <motion.h1
              className="text-2xl font-bold text-slate-800 dark:text-white flex items-center gap-3"
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
          >
            <Users className="w-7 h-7 text-primary-500" />
            面试记录
          </motion.h1>
          <motion.p
              className="text-slate-500 dark:text-slate-400 mt-1"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.1 }}
          >
            查看和管理所有模拟面试记录
          </motion.p>
        </div>

        <motion.div
            className="flex items-center gap-3 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-600 rounded-xl px-4 py-2.5 min-w-[280px] focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-primary-100 dark:focus-within:ring-primary-900/30 transition-all"
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
        >
          <Search className="w-5 h-5 text-slate-400" />
          <input
            type="text"
            placeholder="搜索简历名称..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex-1 outline-none text-slate-700 dark:text-slate-200 placeholder:text-slate-400 bg-transparent"
          />
        </motion.div>
      </div>

      {/* 统计卡片 */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          <StatCard
            icon={Users}
            label="面试总数"
            value={stats.totalCount}
            color="bg-primary-500"
          />
          <StatCard
            icon={CheckCircle}
            label="已完成"
            value={stats.completedCount}
            color="bg-emerald-500"
          />
          <StatCard
            icon={TrendingUp}
            label="平均分数"
            value={stats.averageScore}
            suffix="分"
            color="bg-indigo-500"
          />
        </div>
      )}

      {/* 加载状态 */}
      {loading && (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      )}

      {/* 空状态 */}
      {!loading && filteredInterviews.length === 0 && (
        <motion.div
            className="text-center py-20 bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-700"
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
        >
            <Users className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4"/>
            <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">暂无面试记录</h3>
            <p className="text-slate-500 dark:text-slate-400">开始一次模拟面试后，记录将显示在这里</p>
        </motion.div>
      )}

      {/* 表格 */}
      {!loading && filteredInterviews.length > 0 && (
        <motion.div
            className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700 overflow-hidden"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <table className="w-full">
              <thead className="bg-slate-50 dark:bg-slate-700/50 border-b border-slate-100 dark:border-slate-600">
              <tr>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">关联简历</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">题目数</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">状态</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">得分</th>
                  <th className="text-left px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">创建时间</th>
                  <th className="text-right px-6 py-4 text-sm font-medium text-slate-600 dark:text-slate-300">操作</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {filteredInterviews.map((interview, index) => (
                  <motion.tr
                    key={interview.sessionId}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.05 }}
                    onClick={() => {
                      if (isGeneratingStatus(interview.status)) {
                        setStatusModal({ type: 'generating', interview });
                      } else if (interview.status === 'CREATED') {
                        setStatusModal({ type: 'ready', interview });
                      } else if (interview.status === 'IN_PROGRESS' && onContinueInterview) {
                        onContinueInterview(interview.resumeId);
                      } else {
                        onViewInterview(interview.sessionId, interview.resumeId);
                      }
                    }}
                    className="border-b border-slate-50 dark:border-slate-700 transition-colors group hover:bg-slate-50 dark:hover:bg-slate-700/50 cursor-pointer"
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-slate-400" />
                        <div>
                            <p className="font-medium text-slate-800 dark:text-white">{interview.resumeFilename}</p>
                            <p className="text-xs text-slate-400 dark:text-slate-500">#{interview.sessionId.slice(-8)}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span
                          className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-lg text-sm">
                        {interview.totalQuestions} 题
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <StatusIcon interview={interview} />
                          <span className="text-sm text-slate-600 dark:text-slate-300">
                          {getStatusText(interview)}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      {isEvaluateCompleted(interview) && interview.overallScore !== null ? (
                        <div className="flex items-center gap-3">
                            <div className="w-16 h-2 bg-slate-100 dark:bg-slate-700 rounded-full overflow-hidden">
                            <motion.div
                              className={`h-full ${getScoreColor(interview.overallScore)} rounded-full`}
                              initial={{ width: 0 }}
                              animate={{ width: `${interview.overallScore}%` }}
                              transition={{ duration: 0.8, delay: index * 0.05 }}
                            />
                          </div>
                            <span className="font-bold text-slate-800 dark:text-white">{interview.overallScore}</span>
                        </div>
                      ) : isEvaluating(interview) ? (
                          <span className="text-blue-500 dark:text-blue-400 text-sm">生成中...</span>
                      ) : isEvaluateFailed(interview) ? (
                          <span className="text-red-500 dark:text-red-400 text-sm"
                                title={interview.evaluateError}>失败</span>
                      ) : (
                          <span className="text-slate-400 dark:text-slate-500">-</span>
                      )}
                    </td>
                      <td className="px-6 py-4 text-sm text-slate-500 dark:text-slate-400">
                      {formatDate(interview.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1">
                        {/* 导出按钮 */}
                        {isEvaluateCompleted(interview) && (
                          <button
                            onClick={(e) => handleExport(interview.sessionId, e)}
                            disabled={exporting === interview.sessionId}
                            className="p-2 text-slate-400 hover:text-primary-500 hover:bg-primary-50 dark:hover:bg-primary-900/30 rounded-lg transition-colors disabled:opacity-50"
                            title="导出PDF"
                          >
                            {exporting === interview.sessionId ? (
                              <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                              <Download className="w-4 h-4" />
                            )}
                          </button>
                        )}
                        {/* 删除按钮 */}
                        <button
                          onClick={(e) => handleDeleteClick(interview, e)}
                          disabled={deletingSessionId === interview.sessionId}
                          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors disabled:opacity-50"
                          title="删除"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                          <ChevronRight
                              className="w-5 h-5 text-slate-300 dark:text-slate-600 group-hover:text-primary-500 group-hover:translate-x-1 transition-all"/>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </motion.div>
      )}

      {/* 删除确认对话框 */}
      <DeleteConfirmDialog
        open={deleteItem !== null}
        item={deleteItem ? { id: deleteItem.id, sessionId: deleteItem.sessionId } : null}
        itemType="面试记录"
        loading={deletingSessionId !== null}
        onConfirm={handleDeleteConfirm}
        onCancel={() => setDeleteItem(null)}
      />

      {/* 状态提示弹窗 */}
      <AnimatePresence>
        {statusModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/30 dark:bg-black/50 flex items-center justify-center z-50"
            onClick={() => setStatusModal(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ duration: 0.2 }}
              className="bg-white dark:bg-slate-800 rounded-2xl p-6 max-w-sm w-full mx-4 shadow-xl"
              onClick={e => e.stopPropagation()}
            >
              {statusModal.type === 'generating' ? (
                <>
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-blue-100 dark:bg-blue-900/50 rounded-xl flex items-center justify-center flex-shrink-0">
                      <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />
                    </div>
                    <h3 className="text-lg font-semibold text-slate-800 dark:text-white">题目正在生成中</h3>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 mb-6 text-sm leading-relaxed">
                    AI 正在为您准备面试题目，生成完成后即可开始面试。请稍候片刻，页面将自动刷新。
                  </p>
                  <button
                    onClick={() => setStatusModal(null)}
                    className="w-full px-4 py-2.5 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-xl font-medium hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                  >
                    我知道了
                  </button>
                </>
              ) : (
                <>
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-green-100 dark:bg-green-900/50 rounded-xl flex items-center justify-center flex-shrink-0">
                      <CheckCircle className="w-5 h-5 text-green-500" />
                    </div>
                    <h3 className="text-lg font-semibold text-slate-800 dark:text-white">面试题目已就绪</h3>
                  </div>
                  <p className="text-slate-600 dark:text-slate-400 mb-1 text-sm leading-relaxed">
                    共{' '}
                    <span className="font-medium text-slate-700 dark:text-slate-300">
                      {statusModal.interview.totalQuestions} 道
                    </span>
                    题目
                    {statusModal.interview.followUpCount
                      ? `（含 ${statusModal.interview.followUpCount} 道追问）`
                      : ''}
                    ，点击「开始面试」进入模拟面试。
                  </p>
                  <p className="text-xs text-slate-400 dark:text-slate-500 mb-6">
                    面试将从第一题开始，请保持注意力集中。
                  </p>
                  <div className="flex gap-3">
                    <button
                      onClick={() => setStatusModal(null)}
                      className="flex-1 px-4 py-2.5 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded-xl font-medium hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
                    >
                      稍后再说
                    </button>
                    <button
                      onClick={() => {
                        setStatusModal(null);
                        if (onContinueInterview) onContinueInterview(statusModal.interview.resumeId);
                      }}
                      className="flex-1 px-4 py-2.5 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-colors"
                    >
                      开始面试
                    </button>
                  </div>
                </>
              )}
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}
