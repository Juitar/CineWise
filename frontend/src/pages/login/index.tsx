import { Alert, Button, Input } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'umi';

import { usePasswordLogin } from '../../modules/auth/usePasswordLogin';
import { useAuth } from '../../shared/auth/AuthProvider';
import { resolveSafeReturnUrl } from '../../shared/auth/safeReturnUrl';
import {
  BrandLogoIcon,
  CinemaIllustrationSVG,
  EyeIcon,
  EyeInvisibleIcon,
  HomeIcon,
  LockIcon,
  MailIcon,
} from '../../shared/components/icons';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import './index.css';

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentUser, status: sessionStatus } = useAuth();
  const { errorMessage, isSubmitDisabled, retryRecovery, status, submit } = usePasswordLogin();

  // 响应式屏幕状态判断 (< 1024px 为移动端 / H5 视图)
  const isMobile = useMediaQuery('(max-width: 1023px)');

  // 表单受控状态
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  useEffect(() => {
    if (sessionStatus !== 'authenticated' || !currentUser) {
      return;
    }
    navigate(resolveSafeReturnUrl(searchParams.get('returnUrl'), currentUser.role), {
      replace: true,
    });
  }, [currentUser, navigate, searchParams, sessionStatus]);

  const navigateAfterLogin = (role: 'ADMIN' | 'USER') => {
    navigate(resolveSafeReturnUrl(searchParams.get('returnUrl'), role), {
      replace: true,
    });
  };

  // 表单只负责收集输入，网络调用和结果未知恢复由认证 Hook 统一处理。
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const normalizedEmail = email.trim();
    if (!normalizedEmail || !normalizedEmail.includes('@')) {
      setValidationMessage('请输入有效邮箱');
      return;
    }
    if (password.length < 8 || password.length > 128) {
      setValidationMessage('密码长度必须为 8 到 128 位');
      return;
    }

    setValidationMessage(null);
    const result = await submit(normalizedEmail, password);
    if (result.clearPassword) {
      setPassword('');
    }
    if (result.user) {
      navigateAfterLogin(result.user.role);
    }
  };

  const handleRecovery = async () => {
    const result = await retryRecovery();
    if (result.clearPassword) {
      setPassword('');
    }
    if (result.user) {
      navigateAfterLogin(result.user.role);
    }
  };

  return (
    <div
      className={`login-page-container ${isMobile ? 'login-page--mobile' : 'login-page--desktop'}`}
    >
      <header className="login-header">
        <Link to="/" className="login-header-logo">
          <BrandLogoIcon size={isMobile ? 32 : 34} />
          <span className="login-logo-text">妙语购票</span>
        </Link>

        {!isMobile && (
          <Link to="/" className="login-header-back">
            <HomeIcon size={16} />
            <span>返回首页</span>
          </Link>
        )}
      </header>

      <main className="login-main">
        <div className="login-card">
          <section className="login-banner-section">
            <div className="login-banner-bg-orb" />
            <CinemaIllustrationSVG className="login-banner-illustration" />
            {!isMobile && (
              <div className="login-banner-text">
                <h2 className="login-banner-title">
                  妙语<span className="login-banner-ai-badge">AI</span>智能购票
                </h2>
                <p className="login-banner-subtitle">用自然语言，轻松找到合适的观影方案</p>
              </div>
            )}
          </section>

          <section className="login-form-section">
            <h1 className="login-form-title">登录</h1>
            <p className="login-form-subtitle">登录后继续购票、查看订单与个性化观影服务</p>

            <form className="login-form" onSubmit={handleSubmit}>
              <div className="login-input-group">
                <label className="visually-hidden" htmlFor="email-input">
                  邮箱
                </label>
                <Input
                  id="email-input"
                  type="email"
                  size="large"
                  prefix={<MailIcon size={18} className="login-input-icon-antd" />}
                  placeholder="请输入邮箱"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    setValidationMessage(null);
                  }}
                  aria-label="邮箱"
                  autoComplete="email"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={255}
                />
              </div>

              <div className="login-input-group">
                <label className="visually-hidden" htmlFor="password-input">
                  密码
                </label>
                <Input
                  id="password-input"
                  type={showPassword ? 'text' : 'password'}
                  size="large"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  placeholder="请输入密码"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    setValidationMessage(null);
                  }}
                  aria-label="密码"
                  autoComplete="current-password"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={128}
                  suffix={
                    <button
                      type="button"
                      className="login-input-suffix-antd"
                      onClick={() => setShowPassword(!showPassword)}
                      title={showPassword ? '隐藏密码' : '显示密码'}
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    >
                      {showPassword ? <EyeInvisibleIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  }
                />
              </div>

              {(validationMessage || errorMessage) && (
                <Alert
                  type="error"
                  showIcon
                  message={validationMessage ?? errorMessage}
                  role="alert"
                />
              )}

              <Button
                type="primary"
                htmlType="submit"
                className="login-submit-btn-antd"
                block
                size="large"
                disabled={isSubmitDisabled}
                loading={status === 'submitting' || status === 'recovering'}
              >
                {status === 'recovering' ? '正在确认登录结果' : '登录'}
              </Button>

              {status === 'result-unknown' && (
                <Button className="login-recovery-btn" onClick={() => void handleRecovery()}>
                  重新查询登录结果
                </Button>
              )}

              <div className="login-register-row">
                没有账号？ <Link to="/register">立即注册</Link>
              </div>

              <div className="login-policy-row">
                登录不会更新隐私同意记录 · <Link to="/privacy">查看隐私政策</Link>
              </div>
            </form>
          </section>
        </div>
      </main>
    </div>
  );
}
