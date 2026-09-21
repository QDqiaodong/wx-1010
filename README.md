# wx-1010 冰雪乐园装备与场次绑定系统

## 项目简介

冰雪乐园装备与场次绑定系统，包含 Spring Boot 后端、Vue/Vite 前端、MySQL 和 Redis。项目已统一为 UTF-8 编码，并通过 Docker Compose 固定端口交付。

## 入场发装台（发装—使用—归还闭环）

为“已开始”的场次提供现场发装与归还的完整闭环，前端为独立操作页 `/issue`（菜单“入场发装台”，也可从场次管理“入场发装台”进入指定场次）。

### 数据模型
- `equipment` 新增：
  - `min_temperature`：抗冻下限（℃），结构化字段；为空时后端从 `frost_resistance_spec` 文本解析负温兜底。
  - `current_issue_record_id`：**发装占用锚点**。`NULL`=在库可领，非空=正被某条流水占用；列上有唯一约束，存储层杜绝双重占用。
- 新表 `issue_record`：发装流水，记录场次、绑定器材、器材、游客凭证/姓名/年龄段、实测气温、当时生效抗冻下限、经办人、发装时间，以及状态 `ISSUED / RETURNED / FORCE_CLOSED`、归还经办人、归还时间、兜底原因。
- 场次状态只能经开始/结束接口流转（编辑场次不再改状态）。

### 后端接口
- `POST /api/session/{id}/start`、`POST /api/session/{id}/end`（body 可带 `operator`）：开始/结束场次。
- `GET  /api/session/{id}/issue/overview`：发装台总览（场次状态 + 各绑定器材在库/占用及当前游客 + 本场流水）。
- `GET  /api/session/{id}/issue/records`：按场次查询流水。
- `POST /api/session/{id}/issue`：发装（body：`sessionEquipmentId/visitorId/visitorName/visitorAgeGroup/measuredTemperature/operator`）。
- `PUT  /api/session/{id}/issue/{issueId}/return`：归还（body：`operator`）。

### 四条约束如何被同时守住
1. **并发互斥**：不是“读状态再写布尔位”，而是先写流水，再执行一条带条件的原子抢占
   `UPDATE equipment SET current_issue_record_id=? ... WHERE id=? AND current_issue_record_id IS NULL`。
   数据库行锁保证并发下最多一条命中：命中 1 行才成功，0 行即被他人抢先，整个事务回滚并返回 **HTTP 409「该器材已被其他游客领用」**，落败工作人员明确收到失败。
2. **双校验且都点名**：游客年龄段是否落在器材目标年龄段、实测气温是否低于抗冻下限，两条**独立评估、不短路**；只不满足哪条报哪条，两条都不满足则两条都返回（临界值等于下限视为可用）。
3. **场次时序**：发装/归还先 `SELECT ... FOR UPDATE` 锁场次行，仅 `IN_PROGRESS` 放行；未开始/已结束分别拒绝。**结束场次在同一事务内**把本场所有未归还流水置为 `FORCE_CLOSED`、清空对应器材占用并置为可用，再把场次置为已结束，杜绝“场次结束却仍挂已领用、不能绑定也不能归还”的悬空。
4. **留痕与复用**：每次发装/归还都落 `issue_record`，含经办人、时间、游客、器材、气温与状态；正常归还后清空占用锚点，器材立即可被再次领用。仍在游客手中的器材禁止解绑。

### 验证
后端内置集成测试（H2，真实并发线程/事务）：
- 12 线程并发领同一件 → 恰好 1 成功、11 个 409，失败流水随事务回滚；
- 临界气温（等于下限放行 / 低于 0.1℃ 拒绝）与年龄、气温双校验组合；
- 未开始拒绝、进行中发装、中途结束兜底（流水 `FORCE_CLOSED`、器材释放并可绑新场次再发）、已结束再发/再归还被挡；
- 正常归还后重复领用、防重复归还、流水可查；
- HTTP 层契约：落败为 409 且消息明确，参数错误为 400。

运行：`cd backend && mvn test`。

## 端口

- 前端: http://localhost:3210 / http://127.0.0.1:3210
- 后端 API: http://localhost:3310/api
- MySQL: 127.0.0.1:3410
- Redis: 127.0.0.1:6510

## 构建与启动

```bash
cd /workspace/wx-1010
cd backend && mvn compile -q
cd ../frontend && npm ci && npm run build
cd .. && docker compose up -d --build
```

也可以执行：

```bash
./start.sh
```

> 新增表与列由 JPA `ddl-auto: update` 在启动时自动创建，无需手工建表。

## Docker 构建缓存

- 后端 Dockerfile 先复制 `pom.xml` 和 `settings.xml` 并下载 Maven 依赖，再复制 `src` 编译。
- 前端 Dockerfile 先复制 `package.json` 和 `package-lock.json` 并安装 npm 依赖，再复制源码执行构建。
- `.dockerignore` 排除了 `node_modules`、`dist`、`target`、日志、临时文件、截图和 IDE 配置。
