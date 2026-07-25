# Next Agent Prompt

Copy the prompt below into the next agent session.

```text
你是接力开发 PARVISIGHT Platform 的 Codex agent。请先阅读当前仓库里的交接文档：

D:\Users\yijun\OneDrive\ic_gcl\annotation\AGENT_HANDOFF.md

然后继续在这个仓库工作：

D:\Users\yijun\OneDrive\ic_gcl\annotation

目标不是重新设计项目，而是在现有实现上继续补齐一个 7 日内可交付的产品级本地原型。当前产品聚焦乳腺癌淋巴结 H&E WSI，主流程是：

1. 上传 WSI。
2. 创建病例并绑定一张 Primary H&E WSI。
3. 分配病理医生。
4. 病理医生手动启动 CLAM 分析。
5. 查看热图、候选区域和区域指标。
6. 医生接受、修改、驳回或升级。
7. 条件病例进入高级审核医生队列。
8. 最终临床决策后生成 AI 辅助报告。
9. 结果进入反馈池。

当前已经实现的核心能力：

- Spring/PostgreSQL 本地病例工作流，Flyway V8 已落库。
- 前端本地模式走 Spring API，公网 demo 模式走预计算数据和 localStorage。
- 病例创建抽屉、Primary WSI 绑定、手动 CLAM、医生决策、高级审核、AI 报告和反馈池骨架已经接通。
- `PO-26-0717-001` / `tumor_001.tif` 已完成一次真实 CLAM 到 live LLM 报告的端到端流程。
- `PO-26-0717-006` / `normal_001.tif` 仍是 ready 状态，可用于后续验证，但会修改持久化状态。
- 后端 `mvn test` 通过 18 个测试，前端 `npm run build` 已通过。

请特别注意：

- 不要重置 git 工作区，不要使用 `git reset --hard`，不要恢复用户已删除的 `Hello.java`。
- 不要提交，除非用户明确要求。
- 不要读取、打印、复制、提交或暴露大模型 API key。需要运行本地服务时使用已有脚本加载环境变量。
- 公网 demo 必须保持静态边界：不能开放注册、上传、实时推理、真实凭据或敏感数据。
- 前端设计继续遵循 impeccable skill 的方向：精准、克制、可信，flat by default，英文默认并支持中文切换。

建议你开始时执行：

1. 阅读 `AGENT_HANDOFF.md`、`RUNBOOK.md`、`PRODUCT.md`、`DESIGN.md`。
2. 检查 dirty worktree，但不要回滚任何非你创建的改动。
3. 确认本地服务端口：
   - Frontend: `http://127.0.0.1:5173`
   - Spring API: `http://127.0.0.1:8080`
   - Python API docs: `http://127.0.0.1:8000/docs`
4. 优先运行前端完整视觉回归：
   - `cd frontend`
   - `npm run check:visual`
5. 如果视觉回归通过，再运行：
   - `npm run build:demo`

接下来优先级：

P0. 完成浏览器回归验证。
之前 full visual check 因 case 页面 heatmap loading loop 失败；后续 targeted check 已确认 heatmap 可见。若仍失败，注意报告来源文案实际是 `Validated live model response`，不是 `Live multimodal model response`。

P0. 验证条件高级审核路径：
医生 reject 或 escalate 后，病例应只进入 senior pathologist 队列；senior return 不生成报告，senior approve 才生成报告并进入反馈池。报告需要包含 pathologist 和 senior-review 上下文。

P0. 验证通过浏览器创建病例并绑定新上传 WSI：
上传完成入口、切片卡片 action、Dashboard New case 都要能打开创建抽屉；校验年龄、必填字段、due time、重复绑定；创建后应出现在分配医生工作台。

P1. 增加 focused backend tests：
角色边界、WSI 格式、重复绑定、CLAM review gate、触发阈值、直接完成、条件完成、报告 stale 状态、反馈验证限制。

P1. 清理 MyBatis-Plus composite-key warning：
`ClinicalCaseSlide` 和 `ClinicalCaseReviewTrigger` 不要加假的单列主键。优先补自定义 mapper 方法或采用明确支持的复合键策略。

本地启动命令在仓库根目录执行：

.\scripts\stop-local.ps1
.\scripts\start-local.ps1 -LlmModelOverride "gpt-5.6-sol"

验证命令：

cd backend
mvn test

cd ..\python-service
conda run -n parvisight-py --no-capture-output python -m unittest discover -s tests -v

cd ..\frontend
npm run build
npm run build:demo
npm run check:visual

请先接住项目状态，再开始修改。遇到不确定处，以 `AGENT_HANDOFF.md` 和代码现状为准，不要凭空重构。
```
