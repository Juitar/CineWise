import { Alert, Button, Checkbox, Input } from 'antd';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'umi';

import { CURRENT_PRIVACY_POLICY_VERSION } from '../../modules/auth/types';
import { useRegistration } from '../../modules/auth/useRegistration';
import { useAuth } from '../../shared/auth/AuthProvider';
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
import '../login/index.css';
import './index.css';

function isValidEmail(email: string): boolean {
  return email.length <= 255 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export default function RegisterPage() {
  const navigate = useNavigate();
  const { currentUser, status: sessionStatus } = useAuth();
  const {
    clearFeedback,
    cooldownSeconds,
    isSendCodeDisabled,
    isSubmitDisabled,
    registrationErrorMessage,
    registrationStatus,
    requestCode,
    retryRecovery,
    sendCodeMessage,
    sendCodeStatus,
    submit,
  } = useRegistration();
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [agreement, setAgreement] = useState(false);
  const [validationMessage, setValidationMessage] = useState<string | null>(null);

  useEffect(() => {
    if (sessionStatus === 'authenticated' && currentUser) {
      navigate('/', { replace: true });
    }
  }, [currentUser, navigate, sessionStatus]);

  const handleFieldChange = () => {
    setValidationMessage(null);
    clearFeedback();
  };

  const validateEmail = (): string | null => {
    const normalizedEmail = email.trim();
    if (!isValidEmail(normalizedEmail)) {
      return '请输入有效邮箱';
    }
    return null;
  };

  const handleSendCode = async () => {
    const emailError = validateEmail();
    if (emailError) {
      setValidationMessage(emailError);
      return;
    }
    setValidationMessage(null);
    await requestCode(email.trim());
  };

  const clearSensitiveInputs = () => {
    setCode('');
    setInviteCode('');
    setPassword('');
    setConfirmPassword('');
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const normalizedEmail = email.trim();
    const normalizedNickname = nickname.trim();
    let error = validateEmail();
    if (!error && !/^\d{6}$/.test(code)) {
      error = '验证码必须为 6 位数字';
    } else if (!error && (!inviteCode.trim() || inviteCode.length > 128)) {
      error = '请输入有效邀请码';
    } else if (
      !error &&
      (password.length < 8 ||
        password.length > 20 ||
        !/[A-Za-z]/.test(password) ||
        !/\d/.test(password))
    ) {
      error = '密码必须为 8 到 20 位，并同时包含字母和数字';
    } else if (!error && password !== confirmPassword) {
      error = '两次输入的密码不一致';
    } else if (!error && normalizedNickname.length > 64) {
      error = '昵称长度不能超过 64 位';
    } else if (!error && !agreement) {
      error = '请先阅读并同意隐私政策';
    }

    if (error) {
      setValidationMessage(error);
      return;
    }

    setValidationMessage(null);
    const result = await submit({
      code,
      email: normalizedEmail,
      inviteCode: inviteCode.trim(),
      nickname: normalizedNickname || undefined,
      password,
      privacyPolicyVersion: CURRENT_PRIVACY_POLICY_VERSION,
    });
    if (result.clearSensitiveInputs) {
      clearSensitiveInputs();
    }
    if (result.user) {
      navigate('/', { replace: true });
    }
  };

  const handleRecovery = async () => {
    const result = await retryRecovery();
    if (result.clearSensitiveInputs) {
      clearSensitiveInputs();
    }
    if (result.user) {
      navigate('/', { replace: true });
    }
  };

  const sendButtonText =
    cooldownSeconds > 0
      ? `${cooldownSeconds} 秒后重试`
      : sendCodeStatus === 'sending'
        ? '正在发送'
        : '获取验证码';
  const displayedError = validationMessage ?? registrationErrorMessage;

  return (
    <div
      className={`login-page-container register-page-container ${
        isMobile ? 'login-page--mobile' : 'login-page--desktop'
      }`}
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
            <h1 className="login-form-title">注册</h1>
            <p className="login-form-subtitle">注册后即可购票、查看订单与个性化观影服务</p>

            <div className="register-tabs" aria-label="注册方式">
              <div className="register-tab active">邮箱注册</div>
            </div>

            <form className="login-form register-form" onSubmit={handleSubmit}>
              <div className="login-input-group">
                <Input
                  aria-label="邮箱"
                  autoComplete="email"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={255}
                  placeholder="输入邮箱"
                  prefix={<MailIcon size={18} className="login-input-icon-antd" />}
                  size="large"
                  type="email"
                  value={email}
                  onChange={(event) => {
                    setEmail(event.target.value);
                    handleFieldChange();
                  }}
                />
              </div>

              <div className="login-input-group verify-code-group">
                <Input
                  aria-label="邮箱验证码"
                  autoComplete="one-time-code"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="输入 6 位验证码"
                  prefix={<span className="login-input-icon-antd">校验</span>}
                  size="large"
                  value={code}
                  onChange={(event) => {
                    setCode(event.target.value.replace(/\D/g, '').slice(0, 6));
                    handleFieldChange();
                  }}
                  suffix={
                    <button
                      type="button"
                      className="send-code-btn"
                      disabled={isSendCodeDisabled || !isValidEmail(email.trim())}
                      onClick={() => void handleSendCode()}
                    >
                      {sendButtonText}
                    </button>
                  }
                />
              </div>

              <div className="login-input-group">
                <Input
                  aria-label="邀请码"
                  autoComplete="off"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={128}
                  placeholder="输入培训邀请码"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  size="large"
                  value={inviteCode}
                  onChange={(event) => {
                    setInviteCode(event.target.value);
                    handleFieldChange();
                  }}
                />
              </div>

              <div className="login-input-group">
                <Input
                  aria-label="昵称（选填）"
                  autoComplete="nickname"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={64}
                  placeholder="昵称（选填）"
                  size="large"
                  value={nickname}
                  onChange={(event) => {
                    setNickname(event.target.value);
                    handleFieldChange();
                  }}
                />
              </div>

              <div className="login-input-group register-password-row">
                <Input
                  aria-label="密码"
                  autoComplete="new-password"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={20}
                  placeholder="设置密码"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  size="large"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    handleFieldChange();
                  }}
                  suffix={
                    <button
                      type="button"
                      className="login-input-suffix-antd"
                      onClick={() => setShowPassword(!showPassword)}
                      aria-label={showPassword ? '隐藏密码' : '显示密码'}
                    >
                      {showPassword ? <EyeInvisibleIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  }
                />

                <Input
                  aria-label="确认密码"
                  autoComplete="new-password"
                  className="login-input-antd"
                  disabled={isSubmitDisabled}
                  maxLength={20}
                  placeholder="确认密码"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  size="large"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(event) => {
                    setConfirmPassword(event.target.value);
                    handleFieldChange();
                  }}
                  suffix={
                    <button
                      type="button"
                      className="login-input-suffix-antd"
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      aria-label={showConfirmPassword ? '隐藏确认密码' : '显示确认密码'}
                    >
                      {showConfirmPassword ? <EyeInvisibleIcon size={18} /> : <EyeIcon size={18} />}
                    </button>
                  }
                />
              </div>

              {sendCodeMessage && (
                <Alert
                  message={sendCodeMessage}
                  role={sendCodeStatus === 'error' ? 'alert' : 'status'}
                  showIcon
                  type={sendCodeStatus === 'error' ? 'warning' : 'success'}
                />
              )}

              {displayedError && (
                <Alert message={displayedError} role="alert" showIcon type="error" />
              )}

              <div className="register-agreement">
                <Checkbox
                  checked={agreement}
                  disabled={isSubmitDisabled}
                  onChange={(event) => {
                    setAgreement(event.target.checked);
                    handleFieldChange();
                  }}
                >
                  我已阅读并同意 <Link to="/privacy">《隐私政策》</Link>
                </Checkbox>
              </div>

              <Button
                block
                className="login-submit-btn-antd"
                disabled={isSubmitDisabled}
                htmlType="submit"
                loading={registrationStatus === 'submitting' || registrationStatus === 'recovering'}
                size="large"
                type="primary"
              >
                {registrationStatus === 'recovering' ? '正在确认注册结果' : '注册'}
              </Button>

              {registrationStatus === 'result-unknown' && (
                <Button className="login-recovery-btn" onClick={() => void handleRecovery()}>
                  重新查询注册结果
                </Button>
              )}

              <div className="login-link-row">
                已有账号？ <Link to="/login">立即登录</Link>
              </div>
            </form>
          </section>
        </div>
      </main>
    </div>
  );
}
