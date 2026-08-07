import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError } from '../../shared/api/ApiError';
import {
  getMyProfile,
  grantProfileDataConsent,
  updateMyPersonalization,
  withdrawProfileDataConsent,
  type ProfilePage,
} from './api';

const CONSENT_REQUIRED_CODE = 202004;
const VERSION_CONFLICT_CODE = 202002;

type ProfileState = 'consent-required' | 'error' | 'loading' | 'ready';

/** 画像页面缓存只存在当前 Hook 内；202004 和组件卸载都会清除。 */
export function useProfile(privacyPolicyVersion: string) {
  const [profile, setProfile] = useState<ProfilePage | null>(null);
  const [state, setState] = useState<ProfileState>('loading');
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [consentSaving, setConsentSaving] = useState(false);
  const controllerRef = useRef<AbortController | null>(null);
  const savingRef = useRef(false);
  const consentSavingRef = useRef(false);

  const clearForConsent = useCallback(() => {
    setProfile(null);
    setState('consent-required');
    setNotice('未开启画像数据使用');
  }, []);

  const handleReadError = useCallback(
    (error: unknown, recoveringConsentWrite: boolean) => {
      if (error instanceof ApiError && error.code === CONSENT_REQUIRED_CODE) {
        clearForConsent();
        return;
      }
      setProfile(null);
      setState('error');
      if (recoveringConsentWrite) {
        setNotice('画像开关状态暂时无法确认，请刷新后重试');
      } else if (error instanceof ApiError && error.status === 401) {
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

  const load = useCallback(
    async (recoveringConsentWrite = false) => {
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
          handleReadError(error, recoveringConsentWrite);
        }
      }
    },
    [handleReadError],
  );

  useEffect(() => {
    void load();
    return () => {
      controllerRef.current?.abort();
      setProfile(null);
    };
  }, [load]);

  const setEnabled = useCallback(
    async (enabled: boolean) => {
      if (!profile || savingRef.current || consentSavingRef.current) {
        return;
      }
      savingRef.current = true;
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
        savingRef.current = false;
        setSaving(false);
      }
    },
    [clearForConsent, load, profile],
  );

  const setConsentEnabled = useCallback(
    async (enabled: boolean) => {
      const currentStateAllowsWrite =
        (enabled && state === 'consent-required') || (!enabled && state === 'ready');
      if (!currentStateAllowsWrite || consentSavingRef.current || savingRef.current) {
        return;
      }
      if (enabled && privacyPolicyVersion.trim().length === 0) {
        setNotice('当前隐私政策版本不可用，暂时无法开启用户画像');
        return;
      }

      consentSavingRef.current = true;
      setConsentSaving(true);
      try {
        if (enabled) {
          await grantProfileDataConsent(privacyPolicyVersion);
          await load();
        } else {
          await withdrawProfileDataConsent();
          clearForConsent();
        }
      } catch (error) {
        const shouldReadLatest =
          error instanceof ApiError &&
          (error.status === 409 || error.isResultUnknown || error.kind === 'INVALID_RESPONSE');
        if (shouldReadLatest) {
          await load(true);
        } else if (error instanceof ApiError && error.status === 401) {
          setNotice('登录状态已失效，请重新登录');
        } else if (error instanceof ApiError && error.status === 422) {
          setNotice('画像数据使用设置参数不正确');
        } else if (error instanceof ApiError && (error.status ?? 0) >= 500) {
          setNotice('画像数据使用设置暂时无法保存');
        } else {
          setNotice('画像数据使用设置未保存，请稍后重试');
        }
      } finally {
        consentSavingRef.current = false;
        setConsentSaving(false);
      }
    },
    [clearForConsent, load, privacyPolicyVersion, state],
  );

  return {
    consentEnabled: state === 'ready',
    consentSaving,
    consentStateKnown: state === 'ready' || state === 'consent-required',
    load,
    notice,
    profile,
    saving,
    setConsentEnabled,
    setEnabled,
    state,
  };
}
