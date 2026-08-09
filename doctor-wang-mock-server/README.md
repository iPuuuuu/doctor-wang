# Doctor-Wang 后端服务

这是 Doctor-Wang Android App 当前配套的 Spring Boot 后端。

> **P3 AI 报告处理状态：NOT PRODUCTION READY / BLOCKED。** 本 README 中的本地运行或通用部署说明不构成 AI Service、对象存储、pgvector 或医疗安全能力的上线批准。发布前必须满足 [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) 和 [RELEASE_VALIDATION_CHECKLIST.md](RELEASE_VALIDATION_CHECKLIST.md) 的全部阻断项与证据要求。

它的定位是：
- 本地开发 / 局域网联调用
- 也可先作为第一版公网服务部署上线
- 先保证 App 能“装好即用”

## 本地启动

在这个目录里运行：

```bat
.\gradlew.bat bootRun
```

或者直接双击：

```bat
start-mock-server.bat
```

默认本机地址：

```text
http://127.0.0.1:8080/
```

## 当前环境变量

`application.yml` 已支持通过环境变量覆盖：

- `PORT`：服务端口，默认 `8080`
- `SERVER_ADDRESS`：监听地址，默认 `0.0.0.0`

例如本地手动指定端口：

```bat
set PORT=8080
.\gradlew.bat bootRun
```

## 手机局域网联调步骤

1. 让电脑和手机连接同一个 Wi‑Fi
2. 启动后端服务
3. 在电脑上运行：

```bat
ipconfig
```

4. 找到当前 Wi‑Fi 的 IPv4 地址，例如 `192.168.1.10`
5. 在手机 App → 服务设置中填写：

```text
http://192.168.1.10:8080/
```

6. 点击“保存服务地址”
7. 点击“测试连接”

## 部署到公网环境

推荐目标：
- Render
- Railway
- Fly.io
- 自己的 Linux 服务器 + Docker / Java 17

### 方式一：直接 jar 运行
先构建：

```bat
.\gradlew.bat bootJar
```

产物通常在：

```text
build/libs/
```

启动时确保平台提供 `PORT` 环境变量，应用会自动读取。

### 方式二：Docker 部署
项目已提供 `Dockerfile`。

本地构建镜像示例：

```bat
.\gradlew.bat bootJar
docker build -t doctor-wang-mock-server .
```

本地运行示例：

```bat
docker run -p 8080:8080 doctor-wang-mock-server
```

## Android release 对接方式
Android release 包默认不会再直接依赖局域网地址，而是需要在构建时注入线上地址：

```bat
.\gradlew.bat assembleRelease -PdoctorWangReleaseBaseUrl=https://your-domain.example/
```

或者：

```bat
set DOCTOR_WANG_RELEASE_BASE_URL=https://your-domain.example/
.\gradlew.bat assembleRelease
```

构建完成后，release APK 默认就会连接这个线上地址。

## 接口验证
部署完成后，可以先用浏览器或 curl 验证这些地址：

- `GET /healthz`
- `GET /api/profile`
- `GET /api/health/profile`
- `POST /api/login`
- `POST /api/chat`
- `POST /api/report/upload`
- `GET /api/report/{id}`

### RAG 报告上传（v1）

新上传链路使用真实文件和异步处理任务。当前后端负责文件校验、受控本地存储、用户隔离和任务状态持久化；AI Service 尚未接入时，任务不会伪装为已完成。

```bash
curl -X POST http://localhost:8080/api/v1/reports \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: upload-unique-key" \
  -F "file=@report.pdf;type=application/pdf"
```

支持 `application/pdf`、`image/jpeg`、`image/png`，单文件最大 20 MB。上传成功返回 `202 Accepted`，初始状态固定为 `STORED`，包含 `reportId`、`processingJobId` 和 `statusUrl`。worker 默认关闭；AI Service 未接入时不会生成 `COMPLETED`。

- `GET /api/v1/reports/{reportId}/status`
- `GET /api/v1/reports/{reportId}`
- `POST /api/v1/reports/{reportId}/retry`
- `DELETE /api/v1/reports/{reportId}`

处理状态包括 `STORED`、`PROCESSING`、`RETRYING`、`FAILED`、`COMPLETED`；阶段包括 `STORED`、`EXTRACTING`、`CHUNKING`、`INDEXING`、`ANALYZING`、`SAFETY_REVIEW`、`FINISHED`。稳定契约见 `src/main/resources/openapi/report-v1.yaml`。报告和任务始终按当前登录用户隔离。

上传幂等仅在提供 `Idempotency-Key` 时生效，记录保留 24 小时。请求摘要由文件 SHA-256、规范化的 `reportDate`、规范化的 `reportType` 和当前为空的授权范围按固定顺序计算；同一 key 对应不同摘要会返回冲突。

## PostgreSQL 集成测试

默认测试不需要 Docker：

```bash
bash ./gradlew test
```

PostgreSQL/Flyway 矩阵使用 Testcontainers 和 `pgvector/pgvector:pg16`：

```bash
bash ./gradlew postgresIntegrationTest
```

该矩阵覆盖 Flyway V1–V7、Hibernate schema validation、幂等重放/冲突/过期、补偿清理、级联删除和用户隔离。Docker 不可用时该任务会环境阻断，不能将 smoke 或默认单元测试视为集成验证通过。

例如：

```text
https://your-domain.example/healthz
https://your-domain.example/api/profile
```

## Render 部署示例
仓库中已提供：

```text
render.yaml
Dockerfile
```

你可以直接把 `doctor-wang-mock-server` 目录接入 Render，使用 Docker 方式部署，并把健康检查路径设为：

```text
/healthz
```

## 接口列表

- `POST /api/login`
- `GET /api/profile`
- `GET /api/health/profile`
- `POST /api/chat`
- `POST /api/report/upload`
- `GET /api/report/{id}`
