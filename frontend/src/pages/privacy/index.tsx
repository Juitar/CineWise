import { useNavigate } from 'umi';

import './index.css';

export default function PrivacyPage() {
  const navigate = useNavigate();

  return (
    <main className="privacy-page">
      <article className="privacy-card">
        <p className="privacy-eyebrow">妙语购票</p>
        <h1>隐私政策</h1>
        <p className="privacy-version">静态界面预览 · 正式版本以服务端发布内容为准</p>
        <section>
          <h2>我们处理哪些信息</h2>
          <p>
            账号注册需要邮箱、验证码、邀请码和密码；购票与 Agent
            服务只处理完成对应功能所必需的信息。
          </p>
        </section>
        <section>
          <h2>位置与第三方服务</h2>
          <p>只有在你主动使用路线功能并确认共享后，页面才会申请位置并调用第三方地图服务。</p>
        </section>
        <section>
          <h2>你的选择</h2>
          <p>注册同意框默认不勾选。不同意不会阻止你浏览公开影片和影院信息。</p>
        </section>
        <button className="privacy-back-link" type="button" onClick={() => navigate(-1)}>
          返回
        </button>
      </article>
    </main>
  );
}
