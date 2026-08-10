import React from 'react';

/**
 * 品牌 Logo 图标 (紫色渐变圆角 U / 气泡符号)
 */
export const BrandLogoIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 36,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 40 40"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    className={className}
  >
    <defs>
      <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#8C7BFF" />
        <stop offset="100%" stopColor="#5B4DF0" />
      </linearGradient>
    </defs>
    <path
      d={
        'M12 8C9.79086 8 8 9.79086 8 12V22C8 28.6274 13.3726 34 20 34' +
        'C26.6274 34 32 28.6274 32 22V12C32 9.79086 30.2091 8 28 8' +
        'C25.7909 8 24 9.79086 24 12V21C24 23.2091 22.2091 25 20 25' +
        'C17.7909 25 16 23.2091 16 21V12C16 9.79086 14.2091 8 12 8Z'
      }
      fill="url(#logoGrad)"
    />
  </svg>
);

/**
 * 邮箱/账号图标
 */
export const MailIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 20,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <rect x="2" y="4" width="20" height="16" rx="3" />
    <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
  </svg>
);

/**
 * 密码锁图标
 */
export const LockIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 20,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
  </svg>
);

/**
 * 密码显示/隐藏图标
 */
export const EyeIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 18,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
    <circle cx="12" cy="12" r="3" />
  </svg>
);

export const EyeInvisibleIcon: React.FC<{ size?: number; className?: string }> = ({
  size = 18,
  className = '',
}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <path
      d={
        'M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8 ' +
        'a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4 ' +
        'c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07 ' +
        'a3 3 0 1 1-4.24-4.24'
      }
    />
    <line x1="1" y1="1" x2="23" y2="23" />
  </svg>
);

/**
 * 原型图左侧 3D AI 影院主题矢量插画
 */
export const CinemaIllustrationSVG: React.FC<{ className?: string }> = ({ className = '' }) => (
  <svg viewBox="0 0 440 380" fill="none" xmlns="http://www.w3.org/2000/svg" className={className}>
    <defs>
      {/* 渐变定义 */}
      <linearGradient id="podiumGrad" x1="50%" y1="0%" x2="50%" y2="100%">
        <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.9" />
        <stop offset="100%" stopColor="#E2DDFF" stopOpacity="0.6" />
      </linearGradient>

      <linearGradient id="reelBodyGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#C9BEFF" />
        <stop offset="50%" stopColor="#9B88FF" />
        <stop offset="100%" stopColor="#6C5CE7" />
      </linearGradient>

      <linearGradient id="mascotGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#D5CDFF" />
        <stop offset="100%" stopColor="#8976FF" />
      </linearGradient>

      <linearGradient id="clapperGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stopColor="#A897FF" />
        <stop offset="100%" stopColor="#6E5EEA" />
      </linearGradient>

      <radialGradient id="glowGrad" cx="50%" cy="50%" r="50%">
        <stop offset="0%" stopColor="#BBAEFF" stopOpacity="0.4" />
        <stop offset="100%" stopColor="#BBAEFF" stopOpacity="0" />
      </radialGradient>

      <filter id="softShadow" x="-10%" y="-10%" width="130%" height="130%">
        <feDropShadow dx="0" dy="12" stdDeviation="16" floodColor="#6C5CE7" floodOpacity="0.25" />
      </filter>

      <filter id="floatGlow" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="8" result="blur" />
        <feComposite in="SourceGraphic" in2="blur" operator="over" />
      </filter>
    </defs>

    {/* 背景发光与装饰星芒 */}
    <circle cx="220" cy="190" r="160" fill="url(#glowGrad)" />

    {/* 浮动星芒粒子 */}
    <path
      d="M120 70L123 78L131 81L123 84L120 92L117 84L109 81L117 78Z"
      fill="#B4A4FF"
      opacity="0.8"
    />
    <path
      d="M340 100L342 105L347 107L342 109L340 114L338 109L333 107L338 105Z"
      fill="#8C7CFF"
      opacity="0.7"
    />
    <path
      d="M380 220L383 226L389 229L383 232L380 238L377 232L371 229L377 226Z"
      fill="#C5B9FF"
      opacity="0.9"
    />
    <circle cx="90" cy="220" r="4" fill="#A897FF" opacity="0.6" />
    <circle cx="330" cy="270" r="5" fill="#7C6BFF" opacity="0.5" />

    {/* 顶部气泡小挂件 */}
    <g filter="url(#softShadow)">
      <rect x="250" y="45" width="46" height="34" rx="10" fill="#9B87FF" />
      <path d="M260 79L266 85V79H260Z" fill="#9B87FF" />
      <circle cx="263" cy="62" r="2.5" fill="#FFFFFF" />
      <circle cx="273" cy="62" r="2.5" fill="#FFFFFF" />
      <circle cx="283" cy="62" r="2.5" fill="#FFFFFF" />
    </g>

    {/* 后方窗口卡片饰件 */}
    <rect x="235" y="105" width="105" height="75" rx="12" fill="#E8E2FF" opacity="0.8" />
    <rect x="245" y="115" width="85" height="12" rx="4" fill="#C3B6FF" opacity="0.6" />
    <circle cx="318" cy="121" r="2" fill="#9B88FF" />
    <circle cx="323" cy="121" r="2" fill="#9B88FF" />

    {/* 胶片悬浮长带 */}
    <path
      d="M170 230C210 245 280 250 360 210C390 195 410 160 390 135"
      stroke="#D6CBFF"
      strokeWidth="24"
      strokeLinecap="round"
      opacity="0.5"
    />
    <path
      d="M170 230C210 245 280 250 360 210C390 195 410 160 390 135"
      stroke="#B0A0FF"
      strokeWidth="2"
      strokeDasharray="6 8"
      strokeLinecap="round"
      opacity="0.9"
    />

    {/* 椭圆底座 Podium */}
    <ellipse cx="220" cy="275" rx="150" ry="40" fill="url(#podiumGrad)" filter="url(#softShadow)" />
    <ellipse cx="220" cy="270" rx="140" ry="34" fill="#FFFFFF" opacity="0.9" />

    {/* 主体 3D 胶片盘 (Film Reel) */}
    <g filter="url(#softShadow)">
      <circle cx="210" cy="175" r="75" fill="url(#reelBodyGrad)" />
      <circle cx="210" cy="175" r="62" fill="#D6CDFF" />
      <circle cx="210" cy="175" r="22" fill="url(#reelBodyGrad)" />
      <circle cx="210" cy="175" r="10" fill="#FFFFFF" />

      {/* 胶片盘镂空孔 */}
      <circle cx="210" cy="130" r="14" fill="url(#reelBodyGrad)" />
      <circle cx="210" cy="220" r="14" fill="url(#reelBodyGrad)" />
      <circle cx="165" cy="175" r="14" fill="url(#reelBodyGrad)" />
      <circle cx="255" cy="175" r="14" fill="url(#reelBodyGrad)" />
    </g>

    {/* 场记板 (Clapperboard) */}
    <g filter="url(#softShadow)" transform="translate(115, 190) rotate(-12)">
      <rect x="0" y="20" width="85" height="65" rx="10" fill="url(#clapperGrad)" />
      {/* 播放三角图标 */}
      <path d="M36 42L56 52.5L36 63V42Z" fill="#FFFFFF" />
      {/* 顶板 */}
      <rect x="0" y="0" width="85" height="18" rx="5" fill="#7C6BFF" />
      <path d="M12 0L24 18H14L2 0H12Z" fill="#FFFFFF" opacity="0.9" />
      <path d="M36 0L48 18H38L26 0H36Z" fill="#FFFFFF" opacity="0.9" />
      <path d="M60 0L72 18H62L50 0H60Z" fill="#FFFFFF" opacity="0.9" />
    </g>

    {/* 萌系 AI 吉祥物公仔 (Cute Mascot) */}
    <g filter="url(#softShadow)" transform="translate(265, 195)">
      {/* 身体球体 */}
      <circle cx="35" cy="35" r="35" fill="url(#mascotGrad)" />

      {/* 可爱面部表情 */}
      <ellipse cx="25" cy="30" rx="3.5" ry="4.5" fill="#3B2EB0" />
      <ellipse cx="45" cy="30" rx="3.5" ry="4.5" fill="#3B2EB0" />
      <circle cx="24" cy="28.5" r="1.2" fill="#FFFFFF" />
      <circle cx="44" cy="28.5" r="1.2" fill="#FFFFFF" />

      {/* 微笑嘴巴 */}
      <path d="M30 38C30 41 40 41 40 38" stroke="#3B2EB0" strokeWidth="2.5" strokeLinecap="round" />

      {/* 腮红 */}
      <ellipse cx="18" cy="35" rx="4" ry="2.5" fill="#FF8CB7" opacity="0.7" />
      <ellipse cx="52" cy="35" rx="4" ry="2.5" fill="#FF8CB7" opacity="0.7" />

      {/* 萌萌小手 */}
      <circle cx="-2" cy="42" r="7" fill="#C0B3FF" />
      <circle cx="72" cy="42" r="7" fill="#C0B3FF" />
    </g>
  </svg>
);
