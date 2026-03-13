import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import Layout from './components/Layout';
import { useEffect, useState, Suspense, lazy } from 'react';
import { historyApi } from './api/history';
import type { UploadKnowledgeBaseResponse } from './api/knowledgebase';
import { AuthProvider, useAuth } from './context/AuthContext';

// Lazy load components
const UploadPage = lazy(() => import('./pages/UploadPage'));
const HistoryList = lazy(() => import('./pages/HistoryPage'));
const ResumeDetailPage = lazy(() => import('./pages/ResumeDetailPage'));
const Interview = lazy(() => import('./pages/InterviewPage'));
const InterviewHistoryPage = lazy(() => import('./pages/InterviewHistoryPage'));
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));

// Loading component
const Loading = () => (
  <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800">
    <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
  </div>
);

// 路由守卫：未登录则跳转到登录页
function ProtectedRoute() {
  const { user, isLoading } = useAuth();
  if (isLoading) return <Loading />;
  if (!user) return <Navigate to="/login" replace />;
  return <Layout />;
}

// 已登录用户访问登录/注册页时重定向到主页
function GuestRoute({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuth();
  if (isLoading) return <Loading />;
  if (user) return <Navigate to="/upload" replace />;
  return <>{children}</>;
}

// 上传页面包装器
function UploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (resumeId: number) => {
    navigate('/history', { state: { newResumeId: resumeId } });
  };

  return <UploadPage onUploadComplete={handleUploadComplete} />;
}

// 历史记录列表包装器
function HistoryListWrapper() {
  const navigate = useNavigate();

  const handleSelectResume = (id: number) => {
    navigate(`/history/${id}`);
  };

  return <HistoryList onSelectResume={handleSelectResume} />;
}

// 简历详情包装器
function ResumeDetailWrapper() {
  const { resumeId } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();

  if (!resumeId) {
    return <Navigate to="/history" replace />;
  }

  const handleBack = () => {
    navigate('/history');
  };

  const handleStartInterview = (resumeText: string, resumeId: number) => {
    navigate(`/interview/${resumeId}`, { state: { resumeText } });
  };

  return (
    <ResumeDetailPage
      resumeId={parseInt(resumeId, 10)}
      onBack={handleBack}
      onStartInterview={handleStartInterview}
    />
  );
}

// 模拟面试包装器
function InterviewWrapper() {
  const { resumeId } = useParams<{ resumeId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [resumeText, setResumeText] = useState<string>('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const stateText = (location.state as { resumeText?: string })?.resumeText;
    if (stateText) {
      setResumeText(stateText);
      setLoading(false);
    } else if (resumeId) {
      historyApi.getResumeDetail(parseInt(resumeId, 10))
        .then(resume => {
          setResumeText(resume.resumeText);
          setLoading(false);
        })
        .catch(err => {
          console.error('获取简历文本失败', err);
          setLoading(false);
        });
    } else {
      setLoading(false);
    }
  }, [resumeId, location.state]);

  if (!resumeId) {
    return <Navigate to="/history" replace />;
  }

  const handleBack = () => {
    navigate(`/history/${resumeId}`, { replace: false });
  };

  const handleInterviewComplete = () => {
    navigate('/interviews');
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
          <p className="text-slate-500">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <Interview
      resumeText={resumeText}
      resumeId={parseInt(resumeId, 10)}
      onBack={handleBack}
      onInterviewComplete={handleInterviewComplete}
    />
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<Loading />}>
          <Routes>
            {/* 登录/注册页（未登录可访问） */}
            <Route path="/login" element={<GuestRoute><LoginPage /></GuestRoute>} />
            <Route path="/register" element={<GuestRoute><RegisterPage /></GuestRoute>} />

            {/* 业务页面（需要登录） */}
            <Route path="/" element={<ProtectedRoute />}>
              <Route index element={<Navigate to="/upload" replace />} />
              <Route path="upload" element={<UploadPageWrapper />} />
              <Route path="history" element={<HistoryListWrapper />} />
              <Route path="history/:resumeId" element={<ResumeDetailWrapper />} />
              <Route path="interviews" element={<InterviewHistoryWrapper />} />
              <Route path="interview/:resumeId" element={<InterviewWrapper />} />
              <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />
              <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />
              <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />
            </Route>
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}

// 面试记录页面包装器
function InterviewHistoryWrapper() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate('/upload');
  };

  const handleViewInterview = async (sessionId: string, resumeId?: number) => {
    if (resumeId) {
      navigate(`/history/${resumeId}`, { state: { viewInterview: sessionId } });
    } else {
      try {
        await historyApi.getInterviewDetail(sessionId);
        navigate('/history');
      } catch {
        navigate('/history');
      }
    }
  };

  return <InterviewHistoryPage onBack={handleBack} onViewInterview={handleViewInterview} />;
}

// 知识库管理页面包装器
function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate('/knowledgebase/upload');
  };

  const handleChat = () => {
    navigate('/knowledgebase/chat');
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();
  const location = useLocation();
  const isChatMode = location.pathname === '/knowledgebase/chat';

  const handleBack = () => {
    if (isChatMode) {
      navigate('/knowledgebase');
    } else {
      navigate('/upload');
    }
  };

  const handleUpload = () => {
    navigate('/knowledgebase/upload');
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (_result: UploadKnowledgeBaseResponse) => {
    navigate('/knowledgebase');
  };

  const handleBack = () => {
    navigate('/knowledgebase');
  };

  return <KnowledgeBaseUploadPage onUploadComplete={handleUploadComplete} onBack={handleBack} />;
}

export default App;
