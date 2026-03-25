
## 7.5 批量导入 / 导出（异步任务 + 轮询机制）

大规模病理标注数据的导入与导出是平台与外部 AI 训练流水线衔接的关键通道。导出涉及向量坐标序列化、语义掩码栅格化、Patch 切割等 CPU/IO 密集型操作，耗时从数秒到数分钟不等，不适合在单次 HTTP 请求中同步完成。本系统为导出设计了"提交任务 + 前端轮询"的异步架构，同时支持单张和批量两种模式；导入操作相对轻量，采用同步接口完成。

### 7.5.1 整体架构分层

```
用户操作（前端对话框）
      │  POST 触发 / GET 轮询 / GET 下载
      ▼
ExportController（REST API 入口）
      │  调用 ExportService（公共 API 层）
      ▼
ExportService
  ├─ 校验幻灯片状态（READY + APPROVED）
  ├─ 创建 ExportTask（status=PENDING）
  └─ 调用 ExportAsyncService.doExport() / doBatchExport()
             │  @Async — Spring 线程池异步执行
             ▼
     ExportAsyncService
       ├─ status → PROCESSING
       ├─ 查询 Slide / Label / Annotation
       ├─ 调 PythonServiceClient（掩码 / Patches）
       ├─ 打包 ZipOutputStream
       └─ status → DONE / ERROR，写 zipPath
```

导出触发后 `ExportService.startExport()` 立即返回包含 `taskId` 的 `ExportTask` 对象，HTTP 响应已完成；实际工作在 `ExportAsyncService` 的异步线程中继续推进，前端通过轮询 `GET /api/export-tasks/{taskId}` 感知任务进度。

### 7.5.2 ExportTask 实体与状态机

`ExportTask.java` 是异步任务的持久化载体（`ExportTask.java:8–49`）：

```java
@Data
@TableName("export_tasks")
public class ExportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long   slideId;     // null 表示批量任务
    private String format;      // COCO|YOLO|VOC|GEOJSON|MASK_ONLY|PATCHES
    private String status;      // PENDING→PROCESSING→DONE|ERROR
    private String zipPath;     // DONE 后写入，供下载接口读取
    private Integer patchSize;
    private String  maskType;
    private Integer maskScale;
    private Integer total;      // 总标注数（完成后填写）
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

状态机共四个节点：

| 状态 | 含义 |
|------|------|
| `PENDING` | 任务已入库，等待线程调度 |
| `PROCESSING` | 异步线程已开始执行 |
| `DONE` | ZIP 生成完毕，`zipPath` 已写入 |
| `ERROR` | 执行过程中抛出未捕获异常 |

`ExportFormat` 枚举（`ExportFormat.java:6–23`）定义六种格式：`COCO`、`YOLO`、`VOC`、`GEOJSON`、`MASK_ONLY`、`PATCHES`，`fromString()` 方法在格式字符串为 null 或未知时默认回退到 `COCO`，保证鲁棒性。

### 7.5.3 ExportService：任务创建与参数校验

`ExportService.startExport()` 在触发异步任务前执行两项前置校验（`ExportService.java:33–53`）：

```java
if (!"READY".equals(slide.getStatus()))
    throw new IllegalStateException("Slide not READY ...");
if (!"APPROVED".equals(slide.getReviewStatus()))
    throw new IllegalStateException(messageUtil.msg("error.export.notApproved"));
```

只有处于 `READY`（切片处理完毕）且 `APPROVED`（审核通过）状态的幻灯片才允许导出，这与第 7.2 节审核工作流的设计形成闭环：标注员提交 → 审核员批准 → 才可导出，防止未经审核的标注流入下游训练数据集。

批量入口 `startBatchExport()`（`ExportService.java:65–100`）对 `slideIds` 逐一校验，跳过不合格的幻灯片（记 warn 日志），仅对 `eligible` 列表启动任务；若 eligible 为空则抛出 `IllegalStateException`，整体任务拒绝提交。批量任务的 `ExportTask` 的 `slideId` 字段为 null，以此区分单幻灯片与批量。

### 7.5.4 ExportAsyncService：@Async 异步执行

`AppConfig.java` 用 `@EnableAsync` 开启 Spring 异步支持（`AppConfig.java:22`），并声明专用线程池（`AppConfig.java:55–59`）：

```java
@Bean(name = "maskGenerationExecutor")
public ExecutorService maskGenerationExecutor() {
    // 最多 4 线程，避免压垮 Python 微服务
    return Executors.newFixedThreadPool(4);
}
```

`ExportAsyncService` 拆为独立 Bean 的原因是 Spring `@Async` 基于 AOP 代理：若异步方法与调用方在同一 Bean 中，内部直接调用会绕过代理导致 `@Async` 失效（注释 `ExportAsyncService.java:29–30`）。

**单幻灯片导出流程**（`ExportAsyncService.doExport()`，`ExportAsyncService.java:57–128`）：

```java
@Async
public void doExport(Long taskId) {
    task.setStatus("PROCESSING");
    exportTaskMapper.updateById(task);

    // 1. 加载 Slide / Labels / Annotations
    // 2. 若格式需要掩码（COCO / MASK_ONLY）→ 调 pythonClient.generateMask()
    // 3. 若格式需要 Patch（PATCHES）→ 调 pythonClient.extractPatches()
    // 4. 创建目录，打开 ZipOutputStream 调 packageSingle()
    // 5. task.setStatus("DONE"); task.setZipPath(...);

    // 异常路径
    task.setStatus("ERROR");
}
```

标注数据以 `SlideBundle` record 形式封装（`ExportAsyncService.java:51–52`），包含 `slide`、`labels`、`annotations`、`labelMap`（id → Label）和 `annDicts`（已序列化为 `List<Map>`，供 Python 接口使用），避免在多处方法中重复查库。

**批量导出并行掩码生成**（`ExportAsyncService.doBatchExport()`，`ExportAsyncService.java:133–308`）对 COCO 和 MASK_ONLY 格式采用 `CompletableFuture` 并发调用 Python 微服务：

```java
// ExportAsyncService.java:173–187
List<CompletableFuture<Void>> maskFutures = bundles.stream().map(b ->
    CompletableFuture.runAsync(() -> {
        PythonServiceClient.GenerateMaskResponse r = pythonClient.generateMask(
            b.slide().getId(), b.slide().getWidth(), b.slide().getHeight(),
            b.annDicts(), maskType, maskScale);
        maskPaths.put(b.slide().getId(), r.getMaskPath());   // ConcurrentHashMap
    }, maskExecutor)    // ← 注入的 4 线程池
).toList();
CompletableFuture.allOf(maskFutures.toArray(new CompletableFuture[0])).join();
```

使用 `ConcurrentHashMap` 收集各幻灯片的掩码路径结果，`allOf(...).join()` 等待所有并发任务完成后再进入 ZIP 打包阶段。4 线程上限防止同时向 Python 服务发起过多推理请求造成 OOM 或超时。PATCHES 格式采用相同的并发模式提取各幻灯片的 Patch 目录（`ExportAsyncService.java:191–205`）。

### 7.5.5 ZIP 打包策略

单幻灯片和批量任务均使用 `ZipOutputStream` 打包输出（`ExportAsyncService.java:112–115`，`211–294`），各格式 ZIP 内容如下：

| 格式 | ZIP 内容 |
|------|---------|
| COCO | `images/<slideId>.jpg` + `masks/<slideId>.png` + `instances.json` + `report.txt` |
| YOLO | `images/` + `labels/<name>.txt` + `classes.txt` + `data.yaml` + `report.txt` |
| VOC | `JPEGImages/` + `Annotations/<name>.xml` + `report.txt` |
| GeoJSON | `<name>.geojson` + `report.txt` |
| MASK_ONLY | `masks/<slideId>.png` + `report.txt` |
| PATCHES | `patches/<label>/<uuid>.png` + `labels.csv` + `report.txt` |

批量 COCO 输出单个合并 `instances.json`，`buildStandardCocoJson()` 跨所有幻灯片去重 categories（按 `classIndex` 合并），对 `image_id` 使用 `slide.getId()` 作为全局唯一标识（`ExportAsyncService.java:368–399`）。

### 7.5.6 REST API 设计

`ExportController` 暴露四个端点（`ExportController.java:16–21`）：

```
POST /api/slides/{slideId}/export           → 创建单张导出任务，返回 ExportTask（PENDING）
POST /api/export/batch                      → 创建批量导出任务，返回 ExportTask（PENDING）
GET  /api/export-tasks/{taskId}             → 轮询任务状态
GET  /api/export-tasks/{taskId}/download    → taskId DONE 后流式下载 ZIP
```

下载端点（`ExportController.java:76–97`）使用 `InputStream.transferTo(response.getOutputStream())` 零拷贝流式传输，并设置 `Content-Length` 头支持浏览器进度条，`Content-Disposition: attachment` 强制触发浏览器下载而非预览。

### 7.5.7 前端 ExportDialog：四阶段状态机 + 轮询

`ExportDialog.vue` 以 `phase` 变量驱动界面（`ExportDialog.vue:110`，取值：`idle` / `running` / `done` / `error`），对应四种视图：

- **idle**：格式选择面板（6 种格式 RadioGroup）+ 条件展示 Patch 尺寸 / 掩码类型选项；
- **running**：旋转动画图标 + 流动进度条（`ExportDialog.vue:55–69`），区分 `PENDING`（20%）与 `PROCESSING`（60%）两档伪进度；
- **done**：成功图标 + "下载 ZIP"按钮；
- **error**：错误图标 + 错误信息 + 重试按钮。

轮询逻辑（`ExportDialog.vue:165–185`）：

```js
function startPolling() {
  pollTimer = setInterval(async () => {
    const res = await exportApi.getTask(task.value.id)
    task.value = res.data
    const status = task.value?.status
    if (status === 'DONE') {
      stopPolling()
      phase.value = 'done'
      download()   // 自动触发浏览器下载
    } else if (status === 'ERROR') {
      stopPolling()
      phase.value = 'error'
    }
    // 网络瞬时异常：保持轮询，不切换 phase
  }, 2000)   // 每 2 秒轮询一次
}
```

2 秒轮询间隔兼顾实时感知（单张轻量导出约 3–5 秒即完成）与服务器压力。网络异常时 catch 块不改变 `phase`，轮询继续，避免因一次失败请求误判任务出错。`onClosed()` 钩子（`ExportDialog.vue:214–219`）在关闭对话框时调用 `stopPolling()` 清除计时器，防止游离定时器在组件卸载后继续请求。

### 7.5.8 BatchExportDialog：确认 → 运行 → 完成

`BatchExportDialog.vue` 初始 `phase='confirm'`，展示勾选幻灯片列表（`BatchExportDialog.vue:53–58`）供用户在启动前确认范围。进入 `running` 阶段后采用与 `ExportDialog` 相同的 `setInterval` 轮询（`BatchExportDialog.vue:183–199`），但进度百分比通过 `computed` 映射状态（`BatchExportDialog.vue:153–158`）：

```js
const progressPct = computed(() => {
  if (!task.value)                        return 10
  if (task.value.status === 'PENDING')    return 20
  if (task.value.status === 'PROCESSING') return 60
  return 100
})
```

由于 `doBatchExport` 在服务端是单次原子任务（所有幻灯片在一个 ZIP 中完成），前端无法获取逐幻灯片进度，故使用三段式伪进度（10% → 20% → 60% → 100%）给予用户处理中的视觉反馈，而不呈现假精确的百分比数值。

### 7.5.9 标注导入：同步多格式解析

导入操作相对轻量（纯 JSON/XML/TXT 解析 + DB 写入），采用同步接口无需异步任务。

`ImportController` 暴露单一端点（`ImportController.java:31–50`）：

```
POST /api/slides/{slideId}/annotations/import
  参数：file（标注文件）/ classFile（YOLO 专用 classes.txt）
        format（CUSTOM_JSON|COCO|YOLO|VOC|GEOJSON，默认 COCO）
        mode（REPLACE|MERGE，默认 REPLACE）
```

`ImportService.importAnnotations()` 用 `switch` 表达式分发到五种格式解析器（`ImportService.java:76–80`）：

```java
ImportResult result = switch (format.toUpperCase()) {
    case "CUSTOM_JSON" -> importCustomJson(slideId, ...);
    case "COCO"        -> importCoco(slideId, slide, ...);
    case "YOLO"        -> importYolo(slideId, slide, inputStream, classesStream, ...);
    case "VOC"         -> importVoc(slideId, inputStream, ...);
    case "GEOJSON"     -> importGeoJson(slideId, inputStream, ...);
    default            -> throw new IllegalArgumentException(...);
};
```

**REPLACE 模式**在插入新标注前删除该幻灯片的所有现有标注和标签，保证幂等性（相同文件重复导入结果一致）。**MERGE 模式**则保留现有标注，追加新内容，适用于跨工具联合标注的场景。`ImportService` 为 `@Transactional`，标签创建与标注插入在同一事务中原子执行，任何解析错误均触发全量回滚，不会产生半导入的脏数据。

导入完成后返回 `ImportResult { annotationCount, labelCount, mode }` 供前端展示摘要提示（"已导入 42 条标注 / 3 个标签"），同时写入审计日志（`action=IMPORT`）记录操作来源，与 7.3 节版本历史形成互补：导入操作同样会触发版本快照，使导入前后的标注状态均可溯源。

### 7.5.10 导出 / 导入设计要点总结

| 方面 | 设计决策 | 理由 |
|------|---------|------|
| 异步与同步 | 导出异步、导入同步 | 导出含掩码渲染/Patch 切割（秒级到分钟），导入仅解析文本（毫秒） |
| ExportAsyncService 独立 Bean | 避免 @Async AOP 代理失效 | Spring 自调用绕过代理是常见陷阱 |
| 批量并发上限 4 线程 | 固定线程池 maskGenerationExecutor | 防止同时 N 路 Python 推理请求 OOM |
| 前端 2 秒轮询 | setInterval + transient 异常容忍 | 轻量、无需 SSE/WebSocket；网络抖动不中断体验 |
| 伪进度三段式 | PENDING=20% / PROCESSING=60% / DONE=100% | 服务端无细粒度进度上报，三段保证用户感知不阻塞 |
| REPLACE/MERGE 双模式 | 导入参数控制 | 覆盖式重导与增量合并分别服务不同协作场景 |
| @Transactional 导入 | 标签 + 标注原子提交 | 解析失败时避免半导入的脏数据 |
