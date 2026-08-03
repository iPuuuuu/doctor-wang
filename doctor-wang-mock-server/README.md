# Doctor-Wang Mock Server

这是 Doctor-Wang Android App 当前配套的 Spring Boot 演示后端。

它的定位是：
- 本地开发 / 局域网联调用
- 第一版公网演示后端
- 后续继续承接报告分析、AI Provider 与 Claude 网关能力

---

## 1. 本地启动

在 `doctor-wang-mock-server` 目录里运行：

```bat
.\gradlew.bat bootRun
```

或者先打包：

```bat
.\gradlew.bat bootJar
java -jar build\libs\doctor-wang-mock-server-0.1.0.jar
```

默认本机地址：

```text
http://127.0.0.1:8080/
```

---

## 2. 当前环境变量

`application.yml` 已支持通过环境变量覆盖：

- `PORT`：服务端口，默认 `8080`
- `SERVER_ADDRESS`：监听地址，默认 `0.0.0.0`
- `DOCTOR_WANG_DATA_DIR`：SQLite 数据目录
- `DOCTOR_WANG_UPLOAD_DIR`：上传文件目录
- `DOCTOR_WANG_AI_PROVIDER`：`mock` / `claude`
- `DOCTOR_WANG_AI_MOCK_DEFAULT_ANSWER`：mock AI 默认回答
- `ANTHROPIC_API_KEY`：Claude 网关密钥（仅服务端保存）
- `DOCTOR_WANG_CLAUDE_MODEL`：Claude 模型，默认 `claude-opus-5`

例如本地手动指定端口：

```bat
set PORT=8080
.\gradlew.bat bootRun
```

---

## 3. 手机局域网联调步骤

1. 让电脑和手机连接同一个 Wi‑Fi
2. 启动 mock server
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

---

## 4. 已支持的接口

- `POST /api/login`
- `POST /api/register`
- `GET /api/profile`
- `GET /api/health/profile`
- `PUT /api/profile`
- `POST /api/chat`
- `POST /api/report/upload`
- `GET /api/report`
- `GET /api/report/{id}`
- `GET /api/report/{id}/download`
- `DELETE /api/report/{id}`

说明：
- `chat`、`profile`、`report` 相关接口都要求登录态 / Bearer Token
- 报告接口已经按 `user_id` 做权限隔离
- 报告上传后会立即进入“演示版文本抽取 + 演示版分析”流程

---

## 5. Android release 对接方式

Android release 包通过构建参数注入线上地址，不需要手改源码。

### Windows CMD

```bat
.\gradlew.bat assembleRelease -PdoctorWangReleaseBaseUrl=https://your-domain.example/
```

### 环境变量方式

```bat
set DOCTOR_WANG_RELEASE_BASE_URL=https://your-domain.example/
.\gradlew.bat assembleRelease
```

构建完成后，release APK 默认就会连接这个线上地址。

---

## 6. Linux 服务器部署（systemd）

仓库已提供：

- `doctor-wang-mock-server.service`
- `nginx-doctor-wang.conf`

推荐目录结构：

```text
/opt/doctor-wang/
  doctor-wang-mock-server-0.1.0.jar
  uploads/
  data/
/etc/doctor-wang/
  doctor-wang.env
```

### 示例环境文件 `/etc/doctor-wang/doctor-wang.env`

```text
PORT=8080
SERVER_ADDRESS=0.0.0.0
DOCTOR_WANG_DATA_DIR=/opt/doctor-wang/data
DOCTOR_WANG_UPLOAD_DIR=/opt/doctor-wang/uploads
DOCTOR_WANG_AI_PROVIDER=mock
# ANTHROPIC_API_KEY=your-real-key
# DOCTOR_WANG_CLAUDE_MODEL=claude-opus-5
```

### 安装 service

```bash
sudo cp doctor-wang-mock-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable doctor-wang-mock-server
sudo systemctl start doctor-wang-mock-server
sudo systemctl status doctor-wang-mock-server
```

### 查看日志

```bash
sudo journalctl -u doctor-wang-mock-server -f
```

---

## 7. Nginx / HTTPS 反向代理

仓库提供了基础反向代理模板：

- `nginx-doctor-wang.conf`

推荐生产拓扑：

```text
domain -> Nginx(HTTPS) -> Spring Boot :8080
```

基础步骤：

1. 复制配置到 Nginx 站点目录
2. 把 `doctor-wang.example.com` 改成你的真实域名
3. 检查反代目标是否为 `127.0.0.1:8080`
4. 配置 HTTPS 证书（例如 Let's Encrypt）
5. 打开 80 / 443，关闭不必要公网端口

当前模板已经包含：
- `client_max_body_size 20m`
- `X-Forwarded-*` 头
- 反向代理超时设置

上线时建议再补：
- HTTP → HTTPS 跳转
- 真正的 TLS 证书路径
- access / error log 路径

---

## 8. 部署到 Render / Railway / Fly.io

推荐目标：
- Render
- Railway
- Fly.io
- 自己的 Linux 服务器 + Java 17 + Nginx

### Render
仓库中已提供：

```text
render.yaml
Dockerfile
```

健康检查路径建议设为：

```text
/healthz
```

如果平台支持磁盘卷，建议把：
- SQLite 数据目录
- 上传目录

映射到持久化存储。

---

## 9. 安全发布最低要求

上线前至少确认：

- 只在服务端保存 `ANTHROPIC_API_KEY`
- Android release 包不写死局域网地址
- 上传大小限制已生效
- 上传类型限制已生效（PDF / JPG / PNG）
- Nginx 只开放 80 / 443
- 应用服务只监听内网或本机端口
- 日志中不打印 token / API key / 敏感病历原文

---

## 10. 最终验收建议

建议按下面顺序做一次完整真机验收：

1. 安装 release APK
2. 注册 / 登录
3. 编辑健康档案并重新进入页面确认已保存
4. 发送聊天消息，重启 App 后确认历史仍在
5. 上传 PDF / JPG / PNG 报告
6. 打开病历列表，确认状态和时间正常
7. 进入详情页，确认能看到摘要 / 发现 / 建议
8. 测试下载
9. 测试删除
10. 用另一账号确认不能访问他人的病历
