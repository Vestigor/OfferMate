import {useMemo, useState} from 'react';
import {AnimatePresence, motion} from 'framer-motion';
import {CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {formatDateOnly} from '../utils/date';
import {getScoreColor} from '../utils/score';
import type {InterviewItem} from '../api/history';
import {historyApi} from '../api/history';
import ConfirmDialog from './ConfirmDialog';
import {
  AlertCircle,
  Calendar,
  CheckCircle,
  ChevronRight,
  Download,
  Loader2,
  MessageSquare,
  Mic,
  RefreshCw,
  Trash2,
  TrendingUp,
} from 'lucide-react';

interface InterviewPanelProps {
  interviews: InterviewItem[];
  analyzeStatus?: string;
  onStartInterview: () => void;
  onViewInterview: (sessionId: string, status?: string) => void;
  onStartFromSession: () => void;
  onExportInterview: (sessionId: string) => void;
  onDeleteInterview: (sessionId: string) => void;
  exporting: string | null;
  loadingInterview: boolean;
}

type ModalType = 'generating' | 'ready';

/**
 * 面试记录面板组件
 */
export default function InterviewPanel({
  interviews,
  analyzeStatus,
  onStartInterview,
  onViewInterview,
  onStartFromSession,
  onExportInterview,
  onDeleteInterview,
  exporting,
  loadingInterview
}: InterviewPanelProps) {
  const isAnalyzing = analyzeStatus === 'PENDING' || analyzeStatus === 'PROCESSING';
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<{ sessionId: string } | null>(null);
  const [statusModal, setStatusModal] = useState<{ type: ModalType; interview: InterviewItem } | null>(null);

  const handleCardClick = (interview: InterviewItem) => {
    if (interview.status === 'GENERATING') {
      setStatusModal({ type: 'generating', interview });
      return;
    }
    if (interview.status === 'CREATED') {
      setStatusModal({ type: 'ready', interview });
      return;
    }
    onViewInterview(interview.sessionId, interview.status);
  };

  const handleDeleteClick = (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteConfirm({ sessionId });
  };

  const handleDeleteConfirm = async () => {
    if (!deleteConfirm) return;
    const { sessionId } = deleteConfirm;
    setDeletingSessionId(sessionId);
    try {
      await historyApi.deleteInterview(sessionId);
      onDeleteInterview(sessionId);
      setDeleteConfirm(null);
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败，请稍后重试');
    } finally {
      setDeletingSessionId(null);
    }
  };

  // 准备图表数据（仅包含已评估的记录）
  const chartData = useMemo(() => {
    return interviews
      .filter(i => i.overallScore !== null)
      .map((interview) => ({
        name: formatDateOnly(interview.createdAt),
        score: interview.overallScore || 0,
      }))
      .reverse();
  }, [interviews]);

  if (interviews.length === 0) {
    return (
      <div className="bg-white dark:bg-slate-800 rounded-2xl p-12 text-center">
        <div className="w-16 h-16 mx-auto mb-6 bg-slate-100 dark:bg-slate-700 rounded-full flex items-center justify-center">
          <Mic className="w-8 h-8 text-slate-400" />
        </div>
        <h3 className="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">暂无面试记录</h3>
        <p className="text-slate-500 dark:text-slate-400 mb-6">开始模拟面试，获取专业评估</p>
        <motion.button
          onClick={() => { if (!isAnalyzing) onStartInterview(); }}
          disabled={isAnalyzing}
          className="px-6 py-3 bg-gradient-to-r from-primary-500 to-primary-600 text-white rounded-xl font-medium shadow-lg shadow-primary-500/30 disabled:opacity-50 disabled:cursor-not-allowed disabled:shadow-none disabled:pointer-events-none"
          whileHover={{ scale: isAnalyzing ? 1 : 1.02 }}
          whileTap={{ scale: isAnalyzing ? 1 : 0.98 }}
          title={isAnalyzing ? '简历分析中，请等待分析完成后再开始面试' : undefined}
        >
          {isAnalyzing ? '简历分析中...' : '开始模拟面试'}
        </motion.button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 面试表现趋势图 */}
      {chartData.length > 0 && (
        <motion.div
          className="bg-white dark:bg-slate-800 rounded-2xl p-6"
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-primary-500" />
              <span className="font-semibold text-slate-800 dark:text-white">面试表现趋势</span>
            </div>
            <span className="text-sm text-slate-500 dark:text-slate-400">共 {chartData.length} 场练习</span>
          </div>
          <div className="h-48">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" className="dark:stroke-slate-700"/>
                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 12 }} />
                <YAxis domain={[0, 100]} axisLine={false} tickLine={false} tick={{ fill: '#94a3b8', fontSize: 12 }} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#fff', border: '1px solid #e2e8f0', borderRadius: '12px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}
                  formatter={(value) => [`${value} 分`, '得分']}
                />
                <Line type="monotone" dataKey="score" stroke="#6366f1" strokeWidth={3} dot={{ fill: '#6366f1', strokeWidth: 2, r: 5 }} activeDot={{ r: 8, fill: '#6366f1' }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </motion.div>
      )}

      {/* 历史面试场次 */}
      <motion.div
        className="bg-white dark:bg-slate-800 rounded-2xl p-6"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
      >
        <div className="flex items-center justify-between mb-6">
          <span className="font-semibold text-slate-800 dark:text-white">历史面试场次</span>
        </div>

        <div className="space-y-4">
          {interviews.map((interview, index) => (
            <InterviewItemCard
              key={interview.id}
              interview={interview}
              index={index}
              total={interviews.length}
              exporting={exporting === interview.sessionId}
              deleting={deletingSessionId === interview.sessionId}
              onView={() => handleCardClick(interview)}
              onExport={() => onExportInterview(interview.sessionId)}
              onDelete={(e) => handleDeleteClick(interview.sessionId, e)}
            />
          ))}
        </div>

        <ConfirmDialog
          open={deleteConfirm !== null}
          title="删除面试记录"
          message="确定要删除这条面试记录吗？删除后无法恢复。"
          confirmText="确定删除"
          cancelText="取消"
          confirmVariant="danger"
          loading={deletingSessionId !== null}
          onConfirm={handleDeleteConfirm}
          onCancel={() => setDeleteConfirm(null)}
        />

        {loadingInterview && (
          <div className="fixed inset-0 bg-black/20 dark:bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 flex items-center gap-4">
              <motion.div
                className="w-8 h-8 border-3 border-slate-200 dark:border-slate-600 border-t-primary-500 rounded-full"
                animate={{ rotate: 360 }}
                transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
              />
              <span className="text-slate-600 dark:text-slate-300">加载面试详情...</span>
            </div>
          </div>
        )}
      </motion.div>

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
                        onStartFromSession();
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
    </div>
  );
}

// 面试项卡片组件
function InterviewItemCard({
  interview,
  index,
  total,
  exporting,
  deleting,
  onView,
  onExport,
  onDelete
}: {
  interview: InterviewItem;
  index: number;
  total: number;
  exporting: boolean;
  deleting: boolean;
  onView: () => void;
  onExport: () => void;
  onDelete: (e: React.MouseEvent) => void;
}) {
  const isGenerating = interview.status === 'GENERATING';
  const isEvaluating = interview.evaluateStatus === 'PENDING' || interview.evaluateStatus === 'PROCESSING';
  const isEvaluateFailed = interview.evaluateStatus === 'FAILED';

  return (
    <motion.div
      initial={{ opacity: 0, x: -20 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay: index * 0.1 }}
      onClick={onView}
      className="flex items-center gap-4 p-4 bg-slate-50 dark:bg-slate-700/50 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-700 cursor-pointer transition-colors group"
    >
      {/* 得分 / 状态图标 */}
      {isGenerating ? (
        <div className="w-14 h-14 rounded-full flex items-center justify-center bg-blue-50 dark:bg-blue-900/30 flex-shrink-0">
          <Loader2 className="w-6 h-6 text-blue-400 animate-spin" />
        </div>
      ) : (
        <div className={`w-14 h-14 rounded-full flex items-center justify-center font-bold text-lg flex-shrink-0 ${
          interview.overallScore !== null
            ? getScoreColor(interview.overallScore, [85, 70])
            : 'bg-slate-100 dark:bg-slate-600 text-slate-400'
        }`}>
          {interview.overallScore ?? (interview.status === 'CREATED' || interview.status === 'IN_PROGRESS' ? '…' : '-')}
        </div>
      )}

      {/* 信息 */}
      <div className="flex-1 min-w-0">
        <p className="font-medium text-slate-800 dark:text-white truncate">
          模拟面试 #{total - index}
          {isGenerating && <span className="ml-2 text-sm font-normal text-blue-500 dark:text-blue-400">题目生成中</span>}
          {interview.status === 'CREATED' && <span className="ml-2 text-sm font-normal text-green-500 dark:text-green-400">题目已就绪</span>}
          {interview.status === 'IN_PROGRESS' && <span className="ml-2 text-sm font-normal text-amber-500 dark:text-amber-400">进行中</span>}
        </p>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-slate-500 dark:text-slate-400">
          <span className="flex items-center gap-1">
            <Calendar className="w-4 h-4" />
            {formatDateOnly(interview.createdAt)}
          </span>
          <span className="flex items-center gap-1">
            <MessageSquare className="w-4 h-4" />
            {interview.totalQuestions} 题
            {interview.followUpCount
              ? <span className="text-xs text-slate-400 dark:text-slate-500">（含{interview.followUpCount}追问）</span>
              : null}
          </span>
          {isEvaluating && (
            <span className="flex items-center gap-1 text-xs text-blue-500 dark:text-blue-400">
              <RefreshCw className="w-3 h-3 animate-spin" />
              评估中...
            </span>
          )}
          {isEvaluateFailed && (
            <span className="flex items-center gap-1 text-xs text-red-500 dark:text-red-400">
              <AlertCircle className="w-3 h-3" />
              评估失败
            </span>
          )}
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100">
        <motion.button
          onClick={(e) => { e.stopPropagation(); onExport(); }}
          disabled={exporting}
          className="px-3 py-2 text-slate-400 hover:text-primary-500 hover:bg-white dark:hover:bg-slate-600 rounded-lg transition-all"
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
        >
          <Download className="w-5 h-5" />
        </motion.button>
        <button
          onClick={onDelete}
          disabled={deleting}
          className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="删除面试记录"
        >
          {deleting ? (
            <motion.div
              className="w-5 h-5 border-2 border-red-500 border-t-transparent rounded-full"
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
            />
          ) : (
            <Trash2 className="w-5 h-5" />
          )}
        </button>
      </div>

      <ChevronRight className="w-5 h-5 text-slate-300 dark:text-slate-600 group-hover:text-primary-500 group-hover:translate-x-1 transition-all flex-shrink-0"/>
    </motion.div>
  );
}
