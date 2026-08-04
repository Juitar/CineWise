import { Button, Checkbox, Input } from 'antd';
import React, { useState } from 'react';
import { Link } from 'umi';

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

export default function RegisterPage() {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [agreement, setAgreement] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
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
            <h1 className="login-form-title">注册</h1>
            <p className="login-form-subtitle">注册后即可购票、查看订单与个性化观影服务</p>
            <p className="register-preview-notice" role="status">
              静态页面预览，验证码和注册接口暂未接入
            </p>

            <div className="register-tabs">
              <div className="register-tab active">邮箱注册</div>
            </div>

            <form className="login-form" onSubmit={handleSubmit}>
              <div className="login-input-group">
                <Input
                  aria-label="邮箱"
                  size="large"
                  prefix={<MailIcon size={18} className="login-input-icon-antd" />}
                  placeholder="输入邮箱"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="login-input-antd"
                />
              </div>

              <div className="login-input-group verify-code-group">
                <Input
                  aria-label="邮箱验证码"
                  size="large"
                  prefix={<span className="login-input-icon-antd">🛡️</span>}
                  placeholder="输入验证码"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  className="login-input-antd"
                  suffix={
                    <button type="button" className="send-code-btn" disabled>
                      暂未开放
                    </button>
                  }
                />
              </div>

              <div className="login-input-group">
                <Input
                  type={showPassword ? 'text' : 'password'}
                  size="large"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  placeholder="设置密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="login-input-antd"
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
              </div>

              <div className="login-input-group">
                <Input
                  aria-label="确认密码"
                  type={showConfirmPassword ? 'text' : 'password'}
                  size="large"
                  prefix={<LockIcon size={18} className="login-input-icon-antd" />}
                  placeholder="确认密码"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="login-input-antd"
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

              <div className="register-agreement">
                <Checkbox checked={agreement} onChange={(e) => setAgreement(e.target.checked)}>
                  我已阅读并同意 <Link to="/privacy">《隐私政策》</Link>
                </Checkbox>
              </div>

              <Button
                type="primary"
                htmlType="submit"
                className="login-submit-btn-antd"
                block
                size="large"
                disabled
              >
                注册暂未开放
              </Button>

              <div className="login-link-row">
                已有账号？ <Link to="/login">立即登录</Link>
              </div>
            </form>
          </section>
        </div>
      </main>

      <footer className="login-footer">
        <p>© 2024 妙语购票 · 浙ICP备2024001234号-1 · 浙公网安备 33011002012345号</p>
      </footer>
    </div>
  );
}
