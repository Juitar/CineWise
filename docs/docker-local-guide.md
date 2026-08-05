# CineWise Docker 本地使用指南

本文用于在 Windows 上启动 CineWise 本地联调所需的应用容器。MySQL、Redis 与可选 MinIO 使用团队已配置的共享基础服务。

## 1. Docker 在项目中的作用

Docker 将后端和前端运行在固定版本的容器中。后端通过 `.env` 连接共享 MySQL、Redis 与可选 MinIO，不在成员电脑或应用服务器上启动第二套基础服务。

项目的容器定义位于根目录的 `compose.yaml`：

| 服务 | 作用 | 本机访问地址 |
| --- | --- | --- |
| `backend` | Spring Boot 后端 | `127.0.0.1:8080` |
| `frontend` | Umi 前端与同源反向代理 | `127.0.0.1:8000` |

Docker Desktop 运行在 Windows 上。它会通过 WSL 2 在后台运行 Linux 容器；日常开发仍可使用 Windows、IDEA 和 PowerShell，无需安装或操作 Ubuntu。

## 2. 首次安装 Docker Desktop

1. 安装并启动 Docker Desktop。
2. 选择 WSL 2 后端；Docker Desktop 显示 `Engine running` 后再继续。
3. 关闭旧 PowerShell，重新打开一个终端。
4. 验证 Docker：

```powershell
docker --version
docker compose version
docker version
```

`docker version` 同时显示 Client 和 Server 信息，说明 Docker Engine 已可用。

## 3. 准备本地环境变量

在仓库根目录复制本机环境变量模板：

```powershell
Copy-Item .env.local.example .env
```

然后编辑 `.env`，至少填写以下私密值：

```dotenv
MYSQL_HOST=host.docker.internal
MYSQL_PORT=13306
MYSQL_PASSWORD=云端共享数据库账号密码
REDIS_HOST=host.docker.internal
REDIS_PORT=16379
REDIS_PASSWORD=共享Redis密码
JWT_SECRET=由认证模块负责人确认的JWT密钥
```

规则：

- `.env` 不得提交到 Git；仓库已通过 `.gitignore` 忽略它。
- 本机先启动 A 分配的 SSH 隧道；Docker 容器通过 `host.docker.internal:13306/16379` 访问隧道，最终仍连接云端共享 `cinewise` 与 Redis，不是在本机创建基础服务。
- 本机浏览器统一访问 `http://localhost:8000`，不要在同一调试会话中混用 `127.0.0.1:8000`，两者属于不同 Origin。
- 本机 HTTP 联调使用 `AUTH_COOKIE_SECURE=false`；这只适用于本机或 HTTP 演示入口。
- A 的迁移验证使用独立的 `.env.migration-check` 和云端 `cinewise_migration_check` 库；不得用共享库做首次迁移验证。
- `JWT_SECRET` 由认证模块负责人定义算法和密钥要求；部署负责人只负责安全注入，不在源码或镜像中保存。
- 启用对象存储的模块从 A 获取云端 MinIO 的 `MINIO_ENDPOINT`、最小权限 `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET=cinewise`；不得使用或保存 MinIO root 管理员凭据。

在 `.env` 完整配置前，可以执行 Maven 测试，但不能启动完整 `backend` 容器。

## 4. 启动完整本地环境

在仓库根目录执行：

```powershell
cd D:\Programming\妙语购票\CineWise
docker compose config --quiet
docker compose up -d --build --wait
```

说明：

- 第一次执行会下载镜像并构建后端，耗时通常较长。
- `-d` 表示后台运行。
- `--build` 强制按当前后端代码构建镜像；仅重启已有环境时可以省略。

检查容器状态：

```powershell
docker compose ps
```

预期 `backend` 和 `frontend` 最终都显示为 `healthy` 或正在运行；共享 MySQL 与 Redis 连通性由 backend 日志和整体健康检查确认。

持续查看后端日志：

```powershell
docker compose logs -f backend
```

按 `Ctrl+C` 仅退出日志查看，不会停止容器。

后端成功启动后可访问：

```text
http://localhost:8000
http://localhost:8080/actuator/health
http://localhost:8080/swagger-ui.html
```

## 5. 仅验证共享基础服务配置

应用 Compose 不创建 MySQL、Redis 或 MinIO。只需检查环境变量是否完整时执行：

```powershell
docker compose config --quiet
```

若 `MYSQL_HOST`、`REDIS_HOST`、对应账号密码或 `JWT_SECRET` 缺失，Compose 会在连接任何服务前明确失败。共享服务凭据由对应负责人通过安全渠道提供，不得自行使用生产 root 管理员账号。

## 6. 常用命令

```powershell
# 查看服务状态
docker compose ps

# 查看全部服务日志
docker compose logs

# 查看指定服务最近100行日志
docker compose logs --tail 100 backend

# 停止并删除应用容器、网络
docker compose down

# 再次后台启动已有服务
docker compose up -d

# 暂时停止服务；保留容器和本地数据，下次用 docker compose start 恢复
docker compose stop

# 恢复被 docker compose stop 暂停的服务
docker compose start

```

日常“不想继续运行”时，优先使用 `docker compose stop`：它不会删除容器和数据。下次执行 `docker compose start` 或 `docker compose up -d` 即可恢复。

`docker compose down` 只删除本地应用容器和网络，不会停止或删除共享 MySQL、Redis、MinIO 及其数据。

## 7. 云端 MinIO

MinIO 仅用于海报或演示附件，不是 Docker 本地服务，也不作为交易主链路依赖。云端已有 `cinewise` 桶；对象 Key 统一使用 `posters/` 或 `attachments/` 前缀。

模块确实接入对象存储时，在被 Git 忽略的 `.env` 填写 A 提供的应用账号：

```dotenv
MINIO_ENDPOINT=https://团队提供的云端MinIO地址
MINIO_ACCESS_KEY=应用账号
MINIO_SECRET_KEY=应用账号密钥
MINIO_BUCKET=cinewise
```

不得把 MinIO Console 地址、root 账号或 root 密码交给前端；MinIO 不可用时，前端展示海报占位，交易、支付和电子票流程仍必须可用。

## 8. 常见问题

### Docker Desktop 显示 Engine stopped 或 virtualization support not detected

确认 Windows 已启用 `VirtualMachinePlatform` 和 WSL，且 Hypervisor 在启动时启用。管理员 PowerShell 可执行：

```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
bcdedit /set hypervisorlaunchtype auto
```

然后重启 Windows。

### PowerShell 提示找不到 docker

Docker Desktop 安装后关闭并重新打开 PowerShell。仍找不到时，先确认 Docker Desktop 已启动；不要在旧终端中继续排查 PATH。

### VS Code 的 CMD 提示 `'docker' 不是内部或外部命令`

VS Code 在 Docker 安装前启动时，其内置终端会继承旧的 PATH；即使新建终端也可能找不到 Docker。退出所有 VS Code 窗口后重新打开，再新建终端。

需要立刻临时使用时，在 VS Code 的 CMD 执行：

```bat
set "PATH=%PATH%;C:\Users\admin\AppData\Local\Programs\DockerDesktop\resources\bin"
docker --version
```

这只影响当前终端窗口，重启 VS Code 后不必重复执行。

### Compose 提示无法连接 `dockerDesktopLinuxEngine`

这说明 `docker` 命令已找到，但 Docker Desktop 的 Engine 没有运行。打开 Docker Desktop，等待左下角显示 `Engine running`，再执行：

```powershell
docker version
docker compose up -d
```

`docker version` 同时显示 Client 和 Server 信息，才表示 Engine 可用。

### Compose 提示某个变量 is required

检查 `.env` 中对应变量是否为空、仍是模板占位文字，或拼写错误。常见必填项：

```text
MYSQL_PASSWORD
MYSQL_HOST
MYSQL_DATABASE
MYSQL_USER
REDIS_PASSWORD
JWT_SECRET
```

### 后端容器不健康

按顺序检查：

```powershell
docker compose ps
docker compose logs --tail 100 backend
```

优先检查后端日志中的共享 MySQL、Redis 连接和环境变量错误；应用服务器无法访问基础服务时，核对 VPC/公网地址和固定 `/32` 安全组白名单。

### 端口被占用

如果 8000 或 8080 被其他程序占用，可以先关闭占用程序，或在 `.env` 中修改 `FRONTEND_PORT`、`BACKEND_PORT` 后重新启动。共享 MySQL、Redis 和 MinIO 端口不会映射到本机。

## 9. 团队协作约定

- Docker Compose、Dockerfile 和 `.env.example` 属于共享部署契约，修改前应同步团队。
- `.env`、真实密钥、生产数据和本地数据卷不得提交。
- 新增迁移脚本、环境变量或服务时，必须同步更新 Compose、`.env.example`、OpenSpec 和本指南。
- A 负责应用 Compose、镜像、共享基础服务连接、迁移顺序、健康检查和部署回滚；认证模块负责人负责 JWT 规则与密钥要求；各模块负责人负责自己能力所需的非敏感配置说明。
