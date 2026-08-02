# CineWise Docker 本地使用指南

本文用于在 Windows 上启动 CineWise 本地联调所需的 Redis 和后端容器。MySQL 与 MinIO 使用团队已配置的云端服务。

## 1. Docker 在项目中的作用

Docker 将 Redis 和后端运行在固定版本的容器中。后端通过 `.env` 连接云端共享 MySQL；需要海报或演示附件时，再连接云端 MinIO，不在成员电脑上启动第二套 MySQL 或 MinIO。

项目的容器定义位于根目录的 `compose.yaml`：

| 服务 | 作用 | 本机访问地址 |
| --- | --- | --- |
| `redis` | Redis 7.4 缓存、限流和上下文辅助服务 | `127.0.0.1:6379` |
| `backend` | Spring Boot 后端 | `127.0.0.1:8080` |

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

在仓库根目录复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

然后编辑 `.env`，至少填写以下私密值：

```dotenv
MYSQL_HOST=团队提供的云端MySQL主机
MYSQL_PASSWORD=云端共享数据库账号密码
REDIS_PASSWORD=自行设置的Redis密码
JWT_SECRET=由认证模块负责人确认的JWT密钥
```

规则：

- `.env` 不得提交到 Git；仓库已通过 `.gitignore` 忽略它。
- `.env` 中的 `MYSQL_HOST`、`MYSQL_DATABASE`、`MYSQL_USER` 和 `MYSQL_PASSWORD` 必须指向云端共享 `cinewise` 库。
- A 的迁移验证使用独立的 `.env.migration-check` 和云端 `cinewise_migration_check` 库；不得用共享库做首次迁移验证。
- `JWT_SECRET` 由认证模块负责人定义算法和密钥要求；部署负责人只负责安全注入，不在源码或镜像中保存。
- 启用对象存储的模块从 A 获取云端 MinIO 的 `MINIO_ENDPOINT`、最小权限 `MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` 和 `MINIO_BUCKET=cinewise`；不得使用或保存 MinIO root 管理员凭据。

在 `.env` 完整配置前，可以执行 Maven 测试，但不能启动完整 `backend` 容器。

## 4. 启动完整本地环境

在仓库根目录执行：

```powershell
cd D:\Programming\妙语购票\CineWise
docker compose config --quiet
docker compose up -d --build redis backend
```

说明：

- 第一次执行会下载镜像并构建后端，耗时通常较长。
- `-d` 表示后台运行。
- `--build` 强制按当前后端代码构建镜像；仅重启已有环境时可以省略。

检查容器状态：

```powershell
docker compose ps
```

预期 `redis` 和 `backend` 最终都显示为 `healthy` 或正在运行；云端 MySQL 连通性由 backend 日志和健康检查确认。

持续查看后端日志：

```powershell
docker compose logs -f backend
```

按 `Ctrl+C` 仅退出日志查看，不会停止容器。

后端成功启动后可访问：

```text
http://localhost:8080/actuator/health
http://localhost:8080/swagger-ui.html
```

## 5. 仅启动本地缓存

认证配置尚未就绪，或只需开发缓存相关代码时，可以只启动 Redis：

```powershell
docker compose up -d redis
docker compose ps
```

注意：`compose.yaml` 会解析所有必填环境变量。若 `JWT_SECRET` 尚未提供，Compose 可能在解析时拒绝执行；此时等待认证模块负责人提供正式配置，或仅在团队约定下使用一次性的本地开发配置。

## 6. 常用命令

```powershell
# 查看服务状态
docker compose ps

# 查看全部服务日志
docker compose logs

# 查看指定服务最近100行日志
docker compose logs --tail 100 backend

# 停止并删除容器、网络；保留Redis数据卷
docker compose down

# 再次后台启动已有服务
docker compose up -d

# 暂时停止服务；保留容器和本地数据，下次用 docker compose start 恢复
docker compose stop

# 恢复被 docker compose stop 暂停的服务
docker compose start

# 完全清空本地容器数据，包括Redis数据卷
docker compose down -v
```

日常“不想继续运行”时，优先使用 `docker compose stop`：它不会删除容器和数据。下次执行 `docker compose start` 或 `docker compose up -d` 即可恢复。

`docker compose down` 会删除容器和网络，但保留 Redis 数据卷；下次 `docker compose up -d` 会重新创建容器并继续使用原数据。`docker compose down -v` 只会删除本地 Redis 数据，不会删除云端 MySQL 或云端 MinIO 对象。

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
docker compose logs --tail 100 redis
docker compose logs --tail 100 backend
```

优先确认 Redis 已变为 `healthy`，再检查后端的云端 MySQL 连接、Redis 连接和环境变量错误。

### 端口被占用

如果 6379 或 8080 被其他程序占用，可以先关闭占用程序，或在 `.env` 中修改 `REDIS_PORT`、`BACKEND_PORT` 后重新启动。云端 MySQL 的 3306 不映射到本机端口。

Windows 上曾安装过 Redis 时，确认没有其他项目需要它后，可在“管理员 PowerShell”中停止：

```powershell
Stop-Service -Name Redis
```

## 9. 团队协作约定

- Docker Compose、Dockerfile 和 `.env.example` 属于共享部署契约，修改前应同步团队。
- `.env`、真实密钥、生产数据和本地数据卷不得提交。
- 新增迁移脚本、环境变量或服务时，必须同步更新 Compose、`.env.example`、OpenSpec 和本指南。
- A 负责 Compose、镜像、迁移顺序、健康检查和部署回滚；认证模块负责人负责 JWT 规则与密钥要求；各模块负责人负责自己能力所需的非敏感配置说明。
