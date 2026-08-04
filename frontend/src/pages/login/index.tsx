import { Button, Input } from 'antd';
import React, { useState } from 'react';
import { Link, useLocation } from 'umi';

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
  const location = useLocation();
  const isAdminLogin = location.pathname === '/admin/login';

  // 响应式屏幕状态判断 (< 1024px 为移动端 / H5 视图)
  const isMobile = useMediaQuery('(max-width: 1023px)');

  // 表单受控状态
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // 提交登录表单
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
            <h1 className="login-form-title">{isAdminLogin ? '管理登录' : '登录'}</h1>
            <p className="login-form-subtitle">
              {isAdminLogin
                ? '欢迎使用妙语购票管理后台'
                : '登录后继续购票、查看订单与个性化观影服务'}
            </p>

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
                  onChange={(e) => setEmail(e.target.value)}
                  aria-label="邮箱"
                  className="login-input-antd"
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
                  onChange={(e) => setPassword(e.target.value)}
                  aria-label="密码"
                  className="login-input-antd"
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

              <Button
                type="primary"
                htmlType="submit"
                className="login-submit-btn-antd"
                block
                size="large"
              >
                登录
              </Button>

              {!isAdminLogin && (
                <div className="register-link-row">
                  没有账号？ <Link to="/register">立即注册</Link>
                </div>
              )}
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
