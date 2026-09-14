# wx-1010 冰雪乐园装备与场次绑定系统

## 项目简介

冰雪乐园装备与场次绑定系统，包含 Spring Boot 后端、Vue/Vite 前端、MySQL 和 Redis。项目已统一为 UTF-8 编码，并通过 Docker Compose 固定端口交付。

## 端口

- 前端: http://localhost:3210 / http://127.0.0.1:3210
- 后端 API: http://localhost:3310/api
- MySQL: 127.0.0.1:3410
- Redis: 127.0.0.1:6510

## 构建与启动

```bash
cd /Users/Admin/Desktop/solo-0601/wx-0701/wx-组1/wx-1010
cd backend && mvn compile -q
cd ../frontend && npm ci && npm run build
cd .. && docker compose up -d --build
```

也可以执行：

```bash
./start.sh
```

## Docker 构建缓存

- 后端 Dockerfile 先复制 `pom.xml` 和 `settings.xml` 并下载 Maven 依赖，再复制 `src` 编译。
- 前端 Dockerfile 先复制 `package.json` 和 `package-lock.json` 并安装 npm 依赖，再复制源码执行构建。
- `.dockerignore` 排除了 `node_modules`、`dist`、`target`、日志、临时文件、截图和 IDE 配置。
