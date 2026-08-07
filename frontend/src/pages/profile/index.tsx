import { Link } from 'umi';

import { useLogout } from '../../modules/auth/useLogout';
import { useProfile } from '../../modules/profile/useProfile';
import { useAuth } from '../../shared/auth/AuthProvider';
import './index.css';

const accountStatusLabels = {
  DISABLED: '已停用',
  LOCKED: '已锁定',
  NORMAL: '正常',
} as const;

function profileConsentStatusLabel(enabled: boolean, saving: boolean, stateKnown: boolean): string {
  if (saving) return '正在保存';
  if (!stateKnown) return '状态不可用';
  return enabled ? '已开启' : '未开启';
}

export default function ProfilePage() {
  const { currentUser } = useAuth();
  const { handleLogout, isLoggingOut } = useLogout();
  const {
    consentEnabled,
    consentSaving,
    consentStateKnown,
    notice: profileNotice,
    profile,
    saving: profileSaving,
    setConsentEnabled,
    setEnabled,
    state: profileState,
  } = useProfile(currentUser?.privacyPolicyVersion ?? '');

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
          <Link className="profile-orders-link" to="/orders">
            我的订单
          </Link>
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

      <section className="profile-data-card" aria-labelledby="profile-data-title">
        <div className="profile-data-heading">
          <div>
            <h2 id="profile-data-title">AI 观影画像</h2>
            <p>管理画像数据使用、长期偏好标签和个性化推荐。</p>
          </div>
        </div>

        <div className="profile-consent-setting">
          <div>
            <h3>使用用户画像</h3>
            <p>开启后保存观影偏好；关闭后停止画像写入并清除当前页面画像。</p>
          </div>
          <label className="profile-consent-toggle">
            <input
              aria-label="使用用户画像"
              checked={consentEnabled}
              disabled={!consentStateKnown || consentSaving || profileSaving}
              role="switch"
              type="checkbox"
              onChange={(event) => void setConsentEnabled(event.target.checked)}
            />
            <span>
              {profileConsentStatusLabel(consentEnabled, consentSaving, consentStateKnown)}
            </span>
          </label>
        </div>

        {profile && (
          <div className="profile-personalization-setting">
            <div>
              <h3>个性化推荐</h3>
              <p>关闭后保留画像数据，但推荐时不使用画像偏好。</p>
            </div>
            <label className="profile-personalization-toggle">
              <input
                aria-label="开启个性化"
                checked={profile.preference.enabled}
                disabled={profileSaving || consentSaving}
                type="checkbox"
                onChange={(event) => void setEnabled(event.target.checked)}
              />
              <span>{profileSaving ? '正在保存' : '开启个性化'}</span>
            </label>
          </div>
        )}

        {profileNotice && (
          <p className="profile-data-notice" role="status">
            {profileNotice}
          </p>
        )}
        {profileState === 'loading' && <p>正在读取画像数据...</p>}
        {profileState === 'ready' &&
          profile &&
          (profile.tags.length > 0 ? (
            <ul className="profile-tag-list">
              {profile.tags.map((tag) => (
                <li key={tag.id}>{tag.value}</li>
              ))}
            </ul>
          ) : (
            <p>暂无画像标签</p>
          ))}
      </section>
    </main>
  );
}
