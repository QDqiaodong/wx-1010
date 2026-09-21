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

## 入场发装台（现场发装/归还闭环）

独立操作页：前端菜单「入场发装台」（路由 `/dispatch`，支持 `?sessionId=` 预选场次），也可在「场次管理」行内点「入场发装」进入。

覆盖「发出去—用着—还回来」完整链路，并发与状态约束全部在后端/数据库保证：

1. **同一器材同一时刻只能发给一位游客**：发装先对场次行加悲观写锁串行化同场次操作，再对绑定关系做条件 UPDATE（CAS，仅"在架"可置"已领用"，影响行数 0 即失败），流水表 `outstanding_key` 唯一索引做跨场次兜底。并发领用只有一人成功，其他人收到 HTTP 409「已被其他工作人员领用」。
2. **年龄段 + 气温双校验**：游客年龄段须与器材在本场次的目标年龄段匹配，实测气温不得低于从抗冻规格文本解析出的下限；两条同时校验，任一不满足都会在错误信息中具体指出（两条同时不满足会分别列出）。实测气温等于下限（临界值）允许发装。
3. **场次时序与结束兜底**：只有"进行中"场次可发装；场次结束（专用接口或编辑改状态）在同一事务内把所有"已领用未归还"流水置为"结束兜底收回"、绑定状态复位在架、器材资产状态复位可用，不会出现场次结束后器材悬空、无法归还或无法再绑定的情况。
4. **留痕**：`equipment_dispatch_record` 记录每次发装的器材、游客、游客年龄段、实测气温、抗冻下限、发装人、发装时间，以及归还/兜底的操作人与时间；可按场次查询完整流水。归还后器材立即可重新领用。

接口（均在 `/api/session/{sessionId}/dispatch` 下）：

- `GET /items`：现场视图（绑定器材 + 当前发装状态 + 当前使用游客）
- `GET /records`：按场次查发装/归还流水
- `POST /issue`：发装（请求体含 equipmentId、visitorName、visitorAgeGroup、temperature、operator）
- `POST /records/{recordId}/return`：归还（请求体含 operator）
- 场次状态：`POST /api/session/{id}/start`、`POST /api/session/{id}/end`

后端测试：`cd backend && mvn test`（含 8 线程并发领用、并发归还、临界气温、双校验、场次结束兜底、兜底后新场次重新领用等 17 个用例，使用 H2 MySQL 模式验证真实数据库锁与唯一约束行为）。

