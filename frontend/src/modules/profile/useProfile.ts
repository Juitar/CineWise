import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import { getMyProfile, updateMyPersonalization, type ProfilePage } from './api';

const CONSENT_REQUIRED_CODE = 202004;
const VERSION_CONFLICT_CODE = 202002;

type ProfileState = 'consent-required' | 'error' | 'loading' | 'ready';

/** 画像页面缓存只存在当前 Hook 内；202004 和组件卸载都会清除。 */
export function useProfile() {
  const [profile, setProfile] = useState<ProfilePage | null>(null);
  const [state, setState] = useState<ProfileState>('loading');
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);

  const clearForConsent = useCallback(() => {
    setProfile(null);
    setState('consent-required');
    setNotice('未开启画像数据使用');
  }, []);

  const handleReadError = useCallback(
    (error: unknown) => {
      if (error instanceof ApiError && error.code === CONSENT_REQUIRED_CODE) {
        clearForConsent();
        return;
      }
      setProfile(null);
      setState('error');
      if (error instanceof ApiError && error.status === 401) {
        setNotice('登录状态已失效，请重新登录');
      } else if (error instanceof ApiError && error.status === 403) {
        setNotice('当前账号无权读取画像数据');
      } else if (error instanceof ApiError && error.status === 422) {
        setNotice('画像查询参数不正确');
      } else if (error instanceof ApiError && (error.status ?? 0) >= 500) {
        setNotice('画像服务暂时不可用');
      } else {
        setNotice('画像数据暂时无法读取');
      }
    },
    [clearForConsent],
  );

  const load = useCallback(async () => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setState('loading');
    try {
      const result = await getMyProfile(controller.signal);
      if (!controller.signal.aborted) {
        setProfile(result);
        setState('ready');
        setNotice(null);
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        handleReadError(error);
      }
    }
  }, [handleReadError]);

  useEffect(() => {
    void load();
    return () => {
      controllerRef.current?.abort();
      setProfile(null);
    };
  }, [load]);

  const setEnabled = useCallback(
    async (enabled: boolean) => {
      if (!profile || saving) {
        return;
      }
      setSaving(true);
      try {
        const preference = await updateMyPersonalization(
          enabled,
          profile.preference.version,
          crypto.randomUUID(),
        );
        setProfile((current) => (current ? { ...current, preference } : null));
        setNotice(null);
      } catch (error) {
        if (error instanceof ApiError && error.code === CONSENT_REQUIRED_CODE) {
          clearForConsent();
        } else if (
          error instanceof ApiError &&
          (error.code === VERSION_CONFLICT_CODE || error.status === 409)
        ) {
          setNotice('画像状态已更新，正在读取最新结果');
          await load();
        } else if (error instanceof ApiError && error.status === 422) {
          setNotice('画像设置参数不正确');
        } else if (error instanceof ApiError && (error.status ?? 0) >= 500) {
          setNotice('画像设置暂时无法保存');
        } else {
          setNotice('画像设置未保存，请稍后重试');
        }
      } finally {
        setSaving(false);
      }
    },
    [clearForConsent, load, profile, saving],
  );

  return { load, notice, profile, saving, setEnabled, state };
}
