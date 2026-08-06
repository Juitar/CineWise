import { Alert, Button, Input } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'umi';

import { useEmailCodeLogin } from '../../modules/auth/useEmailCodeLogin';
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

type LoginMode = 'email-code' | 'password';

function isValidEmail(email: string): boolean {
  return email.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentUser, status: sessionStatus } = useAuth();
  const passwordLogin = usePasswordLogin();
  const emailCodeLogin = useEmailCodeLogin();
  const [loginMode, setLoginMode] = useState<LoginMode>('password');
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
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

  const activeStatus = loginMode === 'password' ? passwordLogin.status : emailCodeLogin.status;
  const activeLoginDisabled =
    loginMode === 'password' ? passwordLogin.isSubmitDisabled : emailCodeLogin.isSubmitDisabled;
  const activeErrorMessage =
    loginMode === 'password' ? passwordLogin.errorMessage : emailCodeLogin.errorMessage;

  const handleModeChange = (mode: LoginMode) => {
    if (activeLoginDisabled || mode === loginMode) {
      return;
    }
    setLoginMode(mode);
    setPassword('');
    setCode('');
    setValidationMessage(null);
    passwordLogin.clearFeedback();
    emailCodeLogin.clearFeedback();
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const normalizedEmail = email.trim();
    if (!isValidEmail(normalizedEmail)) {
      setValidationMessage('请输入有效邮箱');
      return;
    }

    setValidationMessage(null);
    if (loginMode === 'password') {
      if (password.length < 8 || password.length > 128) {
        setValidationMessage('密码长度必须为 8 到 128 位');
        return;
      }
      const result = await passwordLogin.submit(normalizedEmail, password);
      if (result.clearPassword) {
        setPassword('');
      }
      if (result.user) {
        navigateAfterLogin(result.user.role);
      }
      return;
    }

    if (!/^\d{6}$/.test(code)) {
      setValidationMessage('验证码必须为 6 位数字');
      return;
    }
    const result = await emailCodeLogin.submit(normalizedEmail, code);
    if (result.clearCode) {
      setCode('');
    }
    if (result.user) {
      navigateAfterLogin(result.user.role);
    }
  };

  const handleRecovery = async () => {
    if (loginMode === 'password') {
      const result = await passwordLogin.retryRecovery();
      if (result.clearPassword) {
        setPassword('');
      }
      if (result.user) {
        navigateAfterLogin(result.user.role);
      }
      return;
    }

    const result = await emailCodeLogin.retryRecovery();
    if (result.clearCode) {
      setCode('');
    }
    if (result.user) {
      navigateAfterLogin(result.user.role);
    }
  };

  const sendCodeText =
    emailCodeLogin.cooldownSeconds > 0
      ? `${emailCodeLogin.cooldownSeconds} 秒后重试`
      : emailCodeLogin.sendCodeStatus === 'sending'
        ? '正在发送'
        : '获取验证码';

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

            <div className="login-mode-tabs" role="tablist" aria-label="登录方式">
              <div
                className={loginMode === 'password' ? 'active' : ''}
                role="tab"
                aria-selected={loginMode === 'password'}
                aria-disabled={activeLoginDisabled}
                tabIndex={activeLoginDisabled ? -1 : 0}
                onClick={() => handleModeChange('password')}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    handleModeChange('password');
                  }
                }}
              >
                密码登录
              </div>
              <div
                className={loginMode === 'email-code' ? 'active' : ''}
                role="tab"
                aria-selected={loginMode === 'email-code'}
                aria-disabled={activeLoginDisabled}
                tabIndex={activeLoginDisabled ? -1 : 0}
                onClick={() => handleModeChange('email-code')}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    handleModeChange('email-code');
                  }
                }}
              >
                验证码登录
              </div>
            </div>

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
                  onChange={(event) => {
                    setEmail(event.target.value);
                    setValidationMessage(null);
                    emailCodeLogin.clearFeedback();
                    passwordLogin.clearFeedback();
                  }}
                  aria-label="邮箱"
                  autoComplete="email"
                  className="login-input-antd"
                  disabled={activeLoginDisabled}
                  maxLength={255}
                />
              </div>

              {loginMode === 'password' ? (
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
                    onChange={(event) => {
                      setPassword(event.target.value);
                      setValidationMessage(null);
                      passwordLogin.clearFeedback();
                    }}
                    aria-label="密码"
                    autoComplete="current-password"
                    className="login-input-antd"
                    disabled={activeLoginDisabled}
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
                  <div className="login-forgot-row">
                    <Link to="/password/reset">忘记密码？</Link>
                  </div>
                </div>
              ) : (
                <div className="login-input-group verify-code-group">
                  <Input
                    aria-label="邮箱验证码"
                    autoComplete="one-time-code"
                    className="login-input-antd"
                    disabled={activeLoginDisabled}
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="请输入 6 位验证码"
                    value={code}
                    onChange={(event) => {
                      setCode(event.target.value.replace(/\D/g, '').slice(0, 6));
                      setValidationMessage(null);
                      emailCodeLogin.clearFeedback();
                    }}
                    suffix={
                      <button
                        type="button"
                        className="send-code-btn"
                        disabled={emailCodeLogin.isSendCodeDisabled || !isValidEmail(email.trim())}
                        onClick={() => void emailCodeLogin.requestCode(email.trim())}
                      >
                        {sendCodeText}
                      </button>
                    }
                  />
                </div>
              )}

              {loginMode === 'email-code' && emailCodeLogin.sendCodeMessage && (
                <Alert
                  type={emailCodeLogin.sendCodeStatus === 'error' ? 'warning' : 'success'}
                  showIcon
                  message={emailCodeLogin.sendCodeMessage}
                  role={emailCodeLogin.sendCodeStatus === 'error' ? 'alert' : 'status'}
                />
              )}

              {(validationMessage || activeErrorMessage) && (
                <Alert
                  type="error"
                  showIcon
                  message={validationMessage ?? activeErrorMessage}
                  role="alert"
                />
              )}

              <Button
                type="primary"
                htmlType="submit"
                className="login-submit-btn-antd"
                block
                size="large"
                disabled={activeLoginDisabled}
                loading={activeStatus === 'submitting' || activeStatus === 'recovering'}
              >
                {activeStatus === 'recovering'
                  ? '正在确认登录结果'
                  : loginMode === 'password'
                    ? '登录'
                    : '验证码登录'}
              </Button>

              {activeStatus === 'result-unknown' && (
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
