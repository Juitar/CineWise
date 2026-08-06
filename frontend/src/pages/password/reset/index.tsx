import { Alert, Button, Input } from 'antd';
import React, { useState } from 'react';
import { Link, useNavigate } from 'umi';

import { usePasswordReset } from '../../../modules/auth/usePasswordReset';
import {
  BrandLogoIcon,
  CinemaIllustrationSVG,
  EyeIcon,
  EyeInvisibleIcon,
  HomeIcon,
  LockIcon,
  MailIcon,
} from '../../../shared/components/icons';
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery';
import '../../login/index.css';
import './index.css';

function isValidEmail(email: string): boolean {
  return email.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPassword(password: string): boolean {
  return (
    password.length >= 8 &&
    password.length <= 20 &&
    /[A-Za-z]/.test(password) &&
    /\d/.test(password)
  );
}

export default function PasswordResetPage() {
  const navigate = useNavigate();
  const reset = usePasswordReset();
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  const clearSensitiveInputs = () => {
    setCode('');
    setNewPassword('');
    setConfirmPassword('');
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const normalizedEmail = email.trim();
    if (!isValidEmail(normalizedEmail)) {
      setValidationMessage('请输入有效邮箱');
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      setValidationMessage('验证码必须为 6 位数字');
      return;
    }
    if (!isValidPassword(newPassword)) {
      setValidationMessage('新密码必须为 8 到 20 位，并同时包含字母和数字');
      return;
    }
    if (newPassword !== confirmPassword) {
      setValidationMessage('两次输入的密码不一致');
      return;
    }
    setValidationMessage(null);
    const result = await reset.submit(normalizedEmail, code, newPassword);
    if (result.clearSensitiveInputs) {
      clearSensitiveInputs();
    }
    if (result.changed) {
      navigate('/login', { replace: true });
    }
  };

  const sendCodeText =
    reset.cooldownSeconds > 0
      ? `${reset.cooldownSeconds} 秒后重试`
      : reset.sendCodeStatus === 'sending'
        ? '正在发送'
        : '获取验证码';

  return (
    <div
      className={`login-page-container password-reset-page ${isMobile ? 'login-page--mobile' : 'login-page--desktop'}`}
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
                <h2 className="login-banner-title">安全重置密码</h2>
                <p className="login-banner-subtitle">验证码和新密码只用于本次操作</p>
              </div>
            )}
          </section>

          <section className="login-form-section">
            <h1 className="login-form-title">重置密码</h1>
            <p className="login-form-subtitle">通过已验证邮箱获取验证码，成功后请使用新密码登录</p>

            <form className="login-form password-reset-form" onSubmit={handleSubmit}>
              <Input
                aria-label="邮箱"
                autoComplete="email"
                className="login-input-antd"
                disabled={reset.isSubmitDisabled}
                maxLength={255}
                placeholder="请输入邮箱"
                prefix={<MailIcon size={18} className="login-input-icon-antd" />}
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  setValidationMessage(null);
                  reset.clearFeedback();
                }}
              />

              <div className="verify-code-group">
                <Input
                  aria-label="邮箱验证码"
                  autoComplete="one-time-code"
                  className="login-input-antd"
                  disabled={reset.isSubmitDisabled}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="请输入 6 位验证码"
                  value={code}
                  onChange={(event) => {
                    setCode(event.target.value.replace(/\D/g, '').slice(0, 6));
                    setValidationMessage(null);
                    reset.clearFeedback();
                  }}
                  suffix={
                    <button
                      type="button"
                      className="send-code-btn"
                      disabled={reset.isSendCodeDisabled || !isValidEmail(email.trim())}
                      onClick={() => void reset.requestCode(email.trim())}
                    >
                      {sendCodeText}
                    </button>
                  }
                />
              </div>

              <div className="password-reset-passwords">
                <Input
                  aria-label="新密码"
                  autoComplete="new-password"
                  className="login-input-antd"
                  disabled={reset.isSubmitDisabled}
                  maxLength={20}
                  placeholder="新密码（8-20 位，含字母和数字）"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(event) => {
                    setNewPassword(event.target.value);
                    setValidationMessage(null);
                    reset.clearFeedback();
                  }}
                  suffix={
                    <button
                      type="button"
                      className="login-input-suffix-antd"
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                      onClick={() => setShowPassword((current) => !current)}
                    >
                      {showPassword ? <EyeInvisibleIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  }
                />
                <Input
                  aria-label="确认新密码"
                  autoComplete="new-password"
                  className="login-input-antd"
                  disabled={reset.isSubmitDisabled}
                  maxLength={20}
                  placeholder="再次输入新密码"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value);
                    setValidationMessage(null);
                    reset.clearFeedback();
                  }}
                />
              </div>

              {reset.sendCodeMessage && (
                <Alert
                  message={reset.sendCodeMessage}
                  role={reset.sendCodeStatus === 'error' ? 'alert' : 'status'}
                  showIcon
                  type={reset.sendCodeStatus === 'error' ? 'warning' : 'success'}
                />
              )}
              {(validationMessage || reset.errorMessage) && (
                <Alert
                  message={validationMessage ?? reset.errorMessage}
                  role="alert"
                  showIcon
                  type="error"
                />
              )}

              <Button
                block
                className="login-submit-btn-antd"
                disabled={reset.isSubmitDisabled}
                htmlType="submit"
                loading={reset.status === 'submitting'}
                size="large"
                type="primary"
              >
                确认重置
              </Button>

              {reset.status === 'result-unknown' && (
                <div className="password-reset-unknown-actions">
                  <Button onClick={() => navigate('/login')}>用新密码尝试登录</Button>
                  <Button
                    onClick={() => {
                      clearSensitiveInputs();
                      reset.startOver();
                    }}
                  >
                    重新开始
                  </Button>
                </div>
              )}

              <div className="login-register-row">
                已想起密码？ <Link to="/login">返回登录</Link>
              </div>
            </form>
          </section>
        </div>
      </main>
    </div>
  );
}
