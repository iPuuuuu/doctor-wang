# Doctor-Wang Mock Server

这是给 Android 真机演示使用的本地假后端。

## 启动方法

在这个目录里运行：

```bat
.\gradlew.bat bootRun
```

或者直接双击：

```bat
start-mock-server.bat
```

默认启动地址：

- `http://127.0.0.1:8080/`

并且已经监听：

- `0.0.0.0:8080`

所以同一 Wi‑Fi 下的手机也可以访问你电脑这台服务。

## 手机联调步骤

1. 让电脑和手机连接同一个 Wi‑Fi
2. 启动 mock server
3. 在电脑上查看自己的局域网 IP
   - 可以在命令行运行：

```bat
ipconfig
```

4. 找到类似 `192.168.1.10` 的 IPv4 地址
5. 在手机安装 Doctor-Wang APK
6. 打开 App → 进入“服务设置”
7. 把服务地址改成：

```text
http://<你的电脑局域网IP>:8080/
```

例如：

```text
http://192.168.1.10:8080/
```

8. 点击“保存服务地址”
9. 点击“测试连接”
10. 连接成功后即可继续登录、查看档案、上传报告、AI 聊天

## 演示接口

- `POST /api/login`
- `GET /api/profile`
- `GET /api/health/profile`
- `POST /api/chat`
- `POST /api/report/upload`
- `GET /api/report/{id}`
