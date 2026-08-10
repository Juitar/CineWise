import { useState } from 'react';
import { Modal } from 'antd';
import { Link } from 'umi';

import type { ProfileTagPolarity, ProfileTagType } from '../../modules/profile/api';
import { useLogout } from '../../modules/auth/useLogout';
import {
  PROFILE_TAG_POLARITY_OPTIONS,
  PROFILE_TAG_STATUS_LABELS,
  PROFILE_TAG_TYPE_LABELS,
  PROFILE_TAG_TYPE_OPTIONS,
  PROFILE_TAG_VALUE_OPTIONS,
} from '../../modules/profile/options';
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

const profileTagSourceLabels = {
  BEHAVIOR: '行为偏好',
  CONVERSATION: '对话偏好',
  MANUAL: '手动设置',
} as const;

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
    createTag,
    deleteTag,
    setConsentEnabled,
    setEnabled,
    state: profileState,
    updateTag,
  } = useProfile(currentUser?.privacyPolicyVersion ?? '');
  const [tagType, setTagType] = useState<ProfileTagType>('MOVIE_GENRE');
  const [tagValue, setTagValue] = useState('');
  const [tagPolarity, setTagPolarity] = useState<ProfileTagPolarity>('LIKE');

  const tagValueOptions =
    tagType === 'CINEMA' || tagType === 'HALL' ? null : PROFILE_TAG_VALUE_OPTIONS[tagType];
  const handleTagTypeChange = (value: ProfileTagType) => {
    setTagType(value);
    setTagValue('');
  };
  const handleCreateTag = async () => {
    const normalizedValue = tagValue.trim();
    if (normalizedValue.length === 0 || normalizedValue.length > 100) {
      return;
    }
    await createTag({
      type: tagType,
      value: normalizedValue,
      polarity: tagPolarity,
      weight: 1,
    });
  };

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
            <p>管理画像数据使用、长期偏好标签和画像偏好的使用范围。</p>
          </div>
        </div>

        <div className="profile-consent-setting">
          <div>
            <h3>记录画像标签</h3>
            <p>开启后，你可以手动添加和管理标签；关闭后停止画像记录，并清除当前页面显示的画像。</p>
          </div>
          <label className="profile-consent-toggle">
            <input
              aria-label="记录画像标签"
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
              <h3>使用画像偏好</h3>
              <p>开启后，相关功能可以使用已保存的画像偏好；关闭后不使用画像偏好，但保留画像数据，你仍可以手动管理标签。</p>
            </div>
            <label className="profile-personalization-toggle">
              <input
                aria-label="使用画像偏好"
                checked={profile.preference.enabled}
                disabled={profileSaving || consentSaving}
                type="checkbox"
                onChange={(event) => void setEnabled(event.target.checked)}
              />
              <span>
                {profileSaving ? '正在保存' : profile.preference.enabled ? '已启用' : '未启用'}
              </span>
            </label>
          </div>
        )}

        {profileNotice && (
          <p className="profile-data-notice" role="status">
            {profileNotice}
          </p>
        )}
        {profileState === 'loading' && <p>正在读取画像数据...</p>}
        {profileState === 'ready' && profile && (
          <>
            <form
              className="profile-tag-form"
              onSubmit={(event) => {
                event.preventDefault();
                void handleCreateTag();
              }}
            >
              <div className="profile-tag-form-field">
                <label htmlFor="profile-tag-type">标签类型</label>
                <select
                  id="profile-tag-type"
                  value={tagType}
                  disabled={profileSaving || consentSaving}
                  onChange={(event) => handleTagTypeChange(event.target.value as ProfileTagType)}
                >
                  {PROFILE_TAG_TYPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="profile-tag-form-field">
                <label htmlFor="profile-tag-value">标签值</label>
                {tagValueOptions ? (
                  <select
                    id="profile-tag-value"
                    value={tagValue}
                    disabled={profileSaving || consentSaving}
                    onChange={(event) => setTagValue(event.target.value)}
                  >
                    <option value="">请选择</option>
                    {tagValueOptions.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    id="profile-tag-value"
                    maxLength={100}
                    placeholder={tagType === 'CINEMA' ? '输入影院名称' : '输入影厅名称'}
                    value={tagValue}
                    disabled={profileSaving || consentSaving}
                    onChange={(event) => setTagValue(event.target.value)}
                  />
                )}
              </div>
              <div className="profile-tag-form-field">
                <label htmlFor="profile-tag-polarity">倾向</label>
                <select
                  id="profile-tag-polarity"
                  value={tagPolarity}
                  disabled={profileSaving || consentSaving}
                  onChange={(event) => setTagPolarity(event.target.value as ProfileTagPolarity)}
                >
                  {PROFILE_TAG_POLARITY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <button
                className="profile-tag-submit"
                disabled={profileSaving || consentSaving || tagValue.trim().length === 0}
                type="submit"
              >
                {profileSaving ? '正在保存' : '添加标签'}
              </button>
            </form>

            {profile.tags.length > 0 ? (
              <ul className="profile-tag-list">
                {profile.tags.map((tag) => {
                  // D 的更新接口只允许 ACTIVE 的手动标签修改倾向；已停用标签必须先恢复。
                  const canChangePolarity = tag.source === 'MANUAL' && tag.status === 'ACTIVE';
                  // CONVERSATION、BEHAVIOR 标签由受控服务维护，用户不能从个人中心改变其状态。
                  const canChangeStatus =
                    tag.source === 'MANUAL' &&
                    (tag.status === 'ACTIVE' || tag.status === 'DISABLED');
                  return (
                    <li className="profile-tag-item" key={tag.id}>
                      <div className="profile-tag-summary">
                        <span className="profile-tag-type">
                          {PROFILE_TAG_TYPE_LABELS[tag.type]}
                        </span>
                        <strong>{tag.value}</strong>
                        <span>{tag.polarity === 'LIKE' ? '喜欢' : '不喜欢'}</span>
                        <span>{PROFILE_TAG_STATUS_LABELS[tag.status]}</span>
                        <span>{profileTagSourceLabels[tag.source]}</span>
                      </div>
                      <div className="profile-tag-actions">
                        {canChangePolarity && (
                          <label className="profile-tag-action-field">
                            <span>修改倾向</span>
                            <select
                              aria-label={`修改${tag.value}倾向`}
                              value={tag.polarity}
                              disabled={profileSaving || consentSaving}
                              onChange={(event) =>
                                void updateTag(tag.id, {
                                  polarity: event.target.value as ProfileTagPolarity,
                                  weight: tag.weight,
                                })
                              }
                            >
                              {PROFILE_TAG_POLARITY_OPTIONS.map((option) => (
                                <option key={option.value} value={option.value}>
                                  {option.label}
                                </option>
                              ))}
                            </select>
                          </label>
                        )}
                        {canChangeStatus && (
                          <button
                            className="profile-tag-action-button"
                            disabled={profileSaving || consentSaving}
                            type="button"
                            onClick={() =>
                              void updateTag(tag.id, {
                                status: tag.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
                              })
                            }
                          >
                            {tag.status === 'ACTIVE' ? '停用' : '恢复'}
                          </button>
                        )}
                        <button
                          className="profile-tag-action-button profile-tag-action-button--danger"
                          disabled={profileSaving || consentSaving}
                          type="button"
                          onClick={() => {
                            Modal.confirm({
                              cancelText: '取消',
                              content: `确定删除“${tag.value}”标签吗？删除后可重新添加。`,
                              okText: '删除',
                              title: '确认删除画像标签',
                              onOk: () => deleteTag(tag.id),
                            });
                          }}
                        >
                          删除
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p>暂无画像标签，请添加你的观影偏好。</p>
            )}
          </>
        )}
      </section>
    </main>
  );
}
