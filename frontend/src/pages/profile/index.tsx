import { Link } from 'umi';

import { useLogout } from '../../modules/auth/useLogout';
import { useAuth } from '../../shared/auth/AuthProvider';
import './index.css';

const accountStatusLabels = {
  DISABLED: '已停用',
  LOCKED: '已锁定',
  NORMAL: '正常',
} as const;

export default function ProfilePage() {
  const { currentUser } = useAuth();
  const { handleLogout, isLoggingOut } = useLogout();

  if (!currentUser) {
    return (
      <main className="profile-page">
        <section className="profile-unavailable" role="alert">
          <h1>暂时无法读取个人资料</h1>
          <p>当前登录信息不完整，请退出后重新登录。</p>
          <button
            className="profile-logout-button"
            disabled={isLoggingOut}
            type="button"
            onClick={() => void handleLogout()}
          >
            {isLoggingOut ? '正在退出' : '退出登录'}
          </button>
        </section>
      </main>
    );
  }

  const avatarText = currentUser.nickname.trim().slice(0, 1) || '妙';

  return (
    <main className="profile-page">
      <header className="profile-page-heading">
        <p className="profile-page-eyebrow">我的账号</p>
        <h1>个人中心</h1>
        <p>查看当前登录账号和隐私信息。</p>
      </header>

      <section className="profile-account-card" aria-labelledby="profile-account-title">
        <div className="profile-account-identity">
          <div className="profile-account-avatar" aria-hidden="true">
            {avatarText}
          </div>
          <div>
            <h2 id="profile-account-title">{currentUser.nickname}</h2>
            <p>{currentUser.emailMasked}</p>
          </div>
        </div>

        <dl className="profile-account-details">
          <div className="profile-account-detail">
            <dt>邮箱验证</dt>
            <dd className={currentUser.emailVerified ? 'profile-status--success' : undefined}>
              {currentUser.emailVerified ? '已验证' : '未验证'}
            </dd>
          </div>
          <div className="profile-account-detail">
            <dt>账号状态</dt>
            <dd className={currentUser.status === 'NORMAL' ? 'profile-status--success' : undefined}>
              {accountStatusLabels[currentUser.status]}
            </dd>
          </div>
          <div className="profile-account-detail">
            <dt>隐私政策版本</dt>
            <dd>{currentUser.privacyPolicyVersion}</dd>
          </div>
        </dl>
      </section>

      <section className="profile-settings-card" aria-labelledby="profile-settings-title">
        <div>
          <h2 id="profile-settings-title">隐私与登录</h2>
          <p>可以查看当前隐私说明，或退出本账号。</p>
        </div>
        <div className="profile-settings-actions">
          <Link className="profile-privacy-link" to="/privacy">
            查看隐私说明
          </Link>
          <button
            className="profile-logout-button"
            disabled={isLoggingOut}
            type="button"
            onClick={() => void handleLogout()}
          >
            {isLoggingOut ? '正在退出' : '退出登录'}
          </button>
        </div>
      </section>
    </main>
  );
}
