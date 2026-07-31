# Doctor-Wang Mock Server

这是 Doctor-Wang Android App 当前配套的 Spring Boot 演示后端。

它的定位是：
- 本地开发 / 局域网联调用
- 也可先作为第一版公网演示后端部署上线
- 继续复用 mock 数据，先保证 App 能“装好即用”

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

## 部署到公网演示环境

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

## 演示接口

- `POST /api/login`
- `GET /api/profile`
- `GET /api/health/profile`
- `POST /api/chat`
- `POST /api/report/upload`
- `GET /api/report/{id}`
