import { useCallback, useRef, useState } from 'react';
import { useNavigate } from 'umi';

import { useAuth } from '../../shared/auth/AuthProvider';

/**
 * 统一处理退出提交、防重复点击和登录页跳转。
 * AuthProvider 会在请求结束时清理浏览器内身份，因此请求失败后也不能继续展示旧用户资料。
 */
export function useLogout() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const inFlight = useRef(false);

  const handleLogout = useCallback(async () => {
    if (inFlight.current) {
      return;
    }

    inFlight.current = true;
    setIsLoggingOut(true);
    try {
      await logout();
    } catch {
      // AuthProvider 已在 finally 中清理本地身份；这里继续离开私有页面，避免显示旧资料。
    } finally {
      navigate('/login', { replace: true });
      inFlight.current = false;
      setIsLoggingOut(false);
    }
  }, [logout, navigate]);

  return { handleLogout, isLoggingOut };
}
