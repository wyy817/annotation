# WSI 标注平台 Web App 需求文档

**项目名称**：基于浏览器的全切片图像（WSI）标注平台
**版本**：v1.0
**作者**：Yuyao Wang（2251773）
**指导教师**：Jingxin Liu
**日期**：2026-03-17

---

## 目录

1. [项目概述](#1-项目概述)
2. [用户角色](#2-用户角色)
3. [功能需求](#3-功能需求)
   - 3.1 工作空间与文件管理模块
   - 3.2 图像上传模块（批量上传配置）
   - 3.3 图像查看模块
   - 3.4 标注交互模块
   - 3.5 后端处理模块
   - 3.6 数据导出模块
4. [非功能需求](#4-非功能需求)
5. [系统架构概述](#5-系统架构概述)
6. [API 接口需求](#6-api-接口需求)
7. [数据模型](#7-数据模型)
8. [约束与排除项](#8-约束与排除项)
9. [验收标准](#9-验收标准)

---

## 1. 项目概述

### 1.1 背景

全切片成像（Whole Slide Imaging, WSI）是现代数字病理学的核心技术，可将传统玻璃切片扫描为超高分辨率数字图像（通常达数万×数万像素级别）。深度学习在计算病理学中的应用高度依赖高质量的标注数据集，然而现有标注工具存在以下核心痛点：

- 需要本地安装，无法通过浏览器直接访问
- 缺乏对巨像素、多分辨率 WSI 的专项支持
- 无法自动生成像素级语义掩码、标准化元数据及深度学习就绪的 patch 数据集
- 工作流割裂，缺乏"上传–标注–导出"一体化流程

### 1.2 目标

开发一套**轻量级、纯浏览器端**的 WSI 标注平台，支持 PNG、JPG、SVS 三种格式，实现：

- 高效的 WSI 多分辨率浏览与导航
- 直观的区域兴趣（ROI）标注工具
- 一键导出深度学习就绪数据集（掩码 + 元数据 + patch）

### 1.3 成功指标

| 指标 | 目标值 |
|------|--------|
| 标注效率提升（对比 LabelMe/QuPath） | ≥ 30% |
| 掩码生成时间 | < 5 秒 |
| 大型 SVS 渲染流畅度 | 无明显卡顿 |
| 支持标注的 WSI 图像数 | ≥ 10 张（评估阶段） |

---

## 2. 用户角色

| 角色 | 描述 | 主要操作 |
|------|------|----------|
| **病理标注员** | 医院/研究机构病理科人员 | 上传切片、浏览图像、绘制标注 |
| **研究员** | 计算病理/AI 研究人员 | 管理标签、导出训练数据集 |
| **系统管理员**（可选） | 平台维护人员 | 管理用户、清理临时文件 |

> 当前 MVP 阶段不强制用户认证，所有用户默认具有完整操作权限。

---

## 3. 功能需求

### 3.1 工作空间与文件管理模块

> 核心设计：用户以**工作空间（Workspace）**为单位组织图像，上传的图像落入用户指定的工作空间，上传完成后立即可标注（即传即用）。

#### FR-01：工作空间管理

- 用户可创建任意数量的工作空间，每个工作空间有：
  - 自定义名称（如 `Lung_Cancer_2026`）
  - 可选描述
  - 创建时间
- 支持重命名、删除工作空间（删除时级联确认，防止误删）
- 首页以**卡片列表**展示所有工作空间，显示：工作空间名称、图像数量、最近更新时间

#### FR-02：文件浏览器

- 进入工作空间后，以**文件浏览器视图**展示其中的图像
- 支持**网格视图**（缩略图）和**列表视图**（文件名、格式、大小、上传时间、标注状态）切换
- 标注状态标识：`未标注` / `标注中` / `已完成`
- 支持按名称、上传时间、格式排序
- 支持搜索（按文件名模糊匹配）
- 点击任意图像直接进入标注界面（即传即用）

#### FR-03：批量上传

支持以下三种上传方式，均可在上传时选择目标工作空间（或新建工作空间）：

| 上传方式 | 说明 |
|----------|------|
| **单文件上传** | 选择一个或多个 PNG/JPG/SVS 文件 |
| **文件夹上传** | 通过 `webkitdirectory` 属性选择整个本地文件夹，自动递归识别其中所有支持格式的文件，忽略其他文件 |
| **压缩包上传** | 上传 `.zip` / `.tar.gz` / `.tar` 压缩包，后端自动解压，递归提取其中的 PNG/JPG/SVS 文件 |

**上传交互细节：**
- 上传前显示文件预览列表，包含：文件名、大小、格式、是否可识别；不支持的文件标红，用户可手动移除
- 每个文件独立显示上传进度条（分块上传，chunk size 5 MB）
- SVS 等大文件支持**断点续传**：上传中断后重新上传可从断点继续
- 全部文件上传完毕后，后端异步触发瓦片预生成，前端实时轮询处理状态（`PROCESSING` → `READY`）
- `READY` 后文件自动出现在文件浏览器中并可点击进入标注

#### FR-04：文件操作

- 支持将文件从一个工作空间**移动**到另一个工作空间
- 支持**删除**单张或多张图像（附带已有标注数据，二次确认）
- 支持下载单张图像的原始文件

---

### 3.2 图像上传模块

#### FR-05：上传参数配置

- 用户在上传对话框中可配置：
  - 目标工作空间（下拉选择 / 输入新建）
  - 压缩包解压时是否**保留原始目录结构**作为子分组（可选，MVP 阶段可简化为平铺）
  - 默认瓦片大小：256 或 512（影响 DeepZoom 生成）

#### FR-06：格式过滤与错误处理

- 文件夹/压缩包中的非支持格式文件（如 `.txt`、`.xlsx`）自动跳过，不报错
- SVS 文件若被 OpenSlide 解析失败，状态置为 `ERROR`，前端显示错误提示，其他文件不受影响
- 单次批量上传文件数上限：**500 个**（超出时提示分批上传）

---

### 3.3 图像查看模块

#### FR-07：多分辨率渲染

- **SVS 文件**：文件上传后，Spring Boot 调用 Python 微服务 `POST /internal/process-slide`，由 OpenSlide 解析金字塔结构，预生成 DeepZoom 瓦片（256×256 PNG）存储至文件系统
- **PNG/JPG 文件**：Python 微服务将普通图像统一转换为 DeepZoom 瓦片，保持与 SVS 相同的渲染管线
- 瓦片目录路径写入 PostgreSQL `slides.tile_dir`，`slides.status` 更新为 `READY`
- 前端通过 Spring Boot 提供的瓦片接口获取 PNG 瓦片，使用 **OpenSeadragon** 渲染，按需懒加载

#### FR-08：缩放与平移

- 支持鼠标滚轮缩放（平滑动画）
- 支持鼠标拖拽平移画布
- 支持键盘快捷键（`+`/`-` 缩放，方向键平移）
- 显示当前缩放倍率（如 `10×`、`40×`）

#### FR-09：小地图导航

- 在视图角落常驻显示整张切片缩略图（mini-map）
- 小地图上以高亮框标识当前视口位置
- 支持点击/拖拽小地图快速跳转到对应区域

#### FR-10：坐标显示

- 实时显示鼠标当前位置对应的原始像素坐标（相对于全分辨率图像）

---

### 3.4 标注交互模块

#### FR-11：标签管理

- 用户可新建标签（Label），每个标签包含：
  - 名称（如 Tumor、Normal、Necrosis）
  - 颜色（颜色选择器）
  - 类别 ID（自动递增）
- 支持编辑和删除标签
- 当前激活标签高亮显示

#### FR-12：画笔工具（Brush）

- 支持自由绘制笔刷标注
- 笔刷大小可调（滑块，范围 5px–200px，基于屏幕像素）
- 笔刷颜色跟随当前激活标签颜色
- 支持笔刷橡皮擦（切换模式即可）

#### FR-13：矩形 ROI 工具

- 拖拽绘制矩形标注框
- 标注框边缘/角可拖拽调整大小
- 支持移动已绘制矩形

#### FR-14：多边形 ROI 工具

- 依次点击节点绘制多边形
- 双击或点击起始点闭合多边形
- 支持拖拽已有节点调整形状
- 支持在多边形边上插入新节点

#### FR-15：标注编辑

- 撤销（Ctrl+Z）/ 重做（Ctrl+Y）操作，历史栈深度 ≥ 20 步
- 选中标注后支持删除（Delete 键）
- 支持修改已有标注的所属标签（类别）

#### FR-16：标注存储

- 所有标注以**矢量格式**（坐标点、多边形、笔刷路径）存储于后端
- 标注数据与图像会话绑定，刷新页面后可恢复
- 支持"保存进度"按钮，手动触发持久化

---

### 3.5 后端处理模块

#### FR-17：像素级语义掩码生成

- Spring Boot Service 从 PostgreSQL 查询当前图像的全部标注数据
- 通过内部 HTTP 调用 Python 微服务 `POST /internal/generate-mask`，传入标注 JSON
- Python 微服务使用 NumPy + OpenCV 将矢量标注渲染为与原始图像等尺寸的掩码图像
- 类别编码规则：
  - `0` = 背景
  - `1, 2, 3, ...` = 对应 `labels.class_index`（用户定义顺序）
- 输出格式：PNG（单通道灰度图），保存至文件系统，路径写回 `slides` 表

#### FR-18：JSON/COCO 元数据生成

生成符合 COCO 格式的 JSON 文件，包含以下字段：

```json
{
  "info": { "image_filename": "...", "width": ..., "height": ..., "format": "SVS" },
  "categories": [{ "id": 1, "name": "Tumor", "color": "#FF0000" }],
  "annotations": [
    {
      "id": 1,
      "category_id": 1,
      "type": "polygon",
      "segmentation": [[x1, y1, x2, y2, ...]],
      "bbox": [x, y, w, h],
      "area": 12345.0
    }
  ]
}
```

#### FR-19：自动 Patch 提取

- Spring Boot 创建 `export_tasks` 记录（状态 `PENDING`），异步触发处理
- 通过内部 HTTP 调用 Python 微服务 `POST /internal/extract-patches`，传入图像路径与标注数据
- Python 微服务根据标注 ROI 从原始图像裁剪 patch
- 默认 patch 尺寸：256×256 或 512×512（由请求参数指定）
- 超出边界的 patch 进行 zero-padding
- 命名规则：`{label_name}_patch_{index:04d}.png`，如 `tumor_patch_0001.png`
- 完成后 Python 微服务回调 Spring Boot，更新 `export_tasks.status = 'DONE'`

---

### 3.6 数据导出模块

#### FR-20：打包下载

用户点击"导出"按钮后，后端生成并提供一个 ZIP 压缩包下载，包含：

```
export_package/
├── original/
│   └── slide.svs（或缩略图 thumbnail.png）
├── masks/
│   └── slide_mask.png
├── annotations/
│   └── annotations.json（COCO 格式）
├── patches/
│   ├── tumor_patch_0001.png
│   ├── normal_patch_0001.png
│   └── ...
└── statistics/
    └── report.json（各类别面积占比等统计信息）
```

#### FR-21：统计报告

`report.json` 包含：

- 各类别标注面积（像素数）
- 各类别占总标注面积的百分比
- 各类别提取的 patch 数量
- 标注完成时间戳

---

## 4. 非功能需求

### 4.1 性能

| 场景 | 要求 |
|------|------|
| 100k×100k 像素 SVS 文件初始加载 | 首帧显示 ≤ 3 秒 |
| 缩放/平移操作响应 | 前端帧率 ≥ 30 FPS |
| 掩码生成（单张 WSI） | ≤ 5 秒 |
| ZIP 打包导出 | ≤ 30 秒（含 patch 提取） |

### 4.2 兼容性

- **浏览器**：Chrome 90+、Firefox 88+、Edge 90+（不要求 Safari）
- **操作系统**：Windows 10+、macOS 11+、Ubuntu 20.04+
- **无需客户端安装**：所有功能通过标准浏览器访问

### 4.3 可用性

- 界面语言：英文（可扩展中文）
- 关键操作支持键盘快捷键
- 标注工具有 Tooltip 提示
- 错误信息使用自然语言描述，不暴露技术堆栈

### 4.4 可靠性

- 标注数据每 60 秒自动保存一次（auto-save）
- 上传失败支持断点续传
- 后端 API 返回结构化错误响应（HTTP 状态码 + 错误消息）

### 4.5 安全性

- 上传文件进行类型校验（MIME type + 文件头魔数），防止恶意文件上传
- 服务端对文件路径进行严格校验，防止路径穿越（path traversal）
- 临时文件定期清理

---

## 5. 系统架构概述

本项目采用**前后端分离 + 图像处理微服务**的三层架构：Vue 3 作为前端，Spring Boot 作为主业务后端，Python 微服务专门处理 WSI 图像（OpenSlide 为 Python 原生库）。数据持久化使用 PostgreSQL，通过 MyBatis Plus 操作。

```
┌──────────────────────────────────────────────────────┐
│                      Frontend                         │
│           Vue 3 + Vite + OpenSeadragon               │
│  ┌──────────┐  ┌───────────────┐  ┌───────────────┐  │
│  │ 上传界面  │  │  WSI 查看器   │  │  标注工具面板  │  │
│  │ (Axios)  │  │ OpenSeadragon │  │  Canvas / SVG │  │
│  └──────────┘  └───────────────┘  └───────────────┘  │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP / REST (JSON)
┌──────────────────────▼───────────────────────────────┐
│              Spring Boot 主后端（Java 17）             │
│                                                       │
│  ┌─────────────────┐   ┌──────────────────────────┐  │
│  │  Controller 层   │   │      Service 层           │  │
│  │  REST API 路由   │   │  业务逻辑 / 文件管理      │  │
│  └────────┬────────┘   └────────────┬─────────────┘  │
│           │                         │                 │
│  ┌────────▼────────────────────────▼─────────────┐   │
│  │               MyBatis Plus（Mapper 层）         │   │
│  │   SlideMapper / AnnotationMapper / LabelMapper │   │
│  └────────────────────────┬───────────────────────┘   │
│                           │ 内部 HTTP 调用              │
│  ┌────────────────────────▼───────────────────────┐   │
│  │          Python 图像处理微服务（FastAPI）         │   │
│  │  OpenSlide + DeepZoom 瓦片生成                  │   │
│  │  NumPy + OpenCV 掩码生成 & Patch 提取           │   │
│  └────────────────────────────────────────────────┘   │
└──────────────────────┬────────────────────────────────┘
                       │
         ┌─────────────┴──────────────┐
         ▼                            ▼
┌─────────────────┐        ┌──────────────────────┐
│   PostgreSQL    │        │   本地文件系统 / NFS   │
│  slides 表      │        │  /uploads/  原始文件   │
│  labels 表      │        │  /tiles/    瓦片缓存   │
│  annotations 表 │        │  /exports/  导出包     │
│  export_tasks 表│        └──────────────────────┘
└─────────────────┘
```

### 技术选型

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | **Vue 3** + Vite | Composition API，组件化开发 |
| UI 组件库 | Element Plus | 表单、弹窗、进度条等基础组件 |
| WSI 渲染 | OpenSeadragon 4.x | 瓦片懒加载，多分辨率浏览 |
| 标注画布 | Fabric.js | Canvas 交互，多边形/矩形/笔刷 |
| HTTP 客户端 | Axios | 前端接口调用 |
| 主后端框架 | **Spring Boot 3.x**（Java 17） | REST API，业务逻辑，文件管理 |
| ORM | **MyBatis Plus 3.5.x** | 单表 CRUD，条件构造器，分页 |
| 数据库 | **PostgreSQL 15+** | 标注数据、图像元数据持久化 |
| 图像处理微服务 | FastAPI（Python 3.10+） | 仅供 Spring Boot 内部调用 |
| WSI 解析 | OpenSlide-Python | 解析 SVS 金字塔结构 |
| 瓦片生成 | openslide DeepZoomGenerator | 生成 256×256 PNG 瓦片 |
| 图像处理 | NumPy + OpenCV-Python | 掩码渲染，Patch 裁剪 |
| 构建工具 | Maven | Spring Boot 项目管理 |

### 服务间通信说明

Spring Boot 主后端通过内部 HTTP（`RestTemplate` 或 `WebClient`）调用 Python 微服务，Python 微服务**不对外暴露**，仅监听 `localhost:8001`。Spring Boot 对外统一暴露 `8080` 端口。

---

## 6. API 接口需求

所有接口由 **Spring Boot Controller** 提供，统一前缀 `/api`，响应体使用统一包装格式：

```json
{ "code": 200, "message": "success", "data": { ... } }
```

错误时：`{ "code": 4xx/5xx, "message": "错误描述", "data": null }`

---

### 6.0 工作空间管理

```
// 创建工作空间
POST /api/workspaces
Body: { "name": "Lung_Cancer_2026", "description": "..." }
Response 200: { "code": 200, "data": { "id": 1, "name": "Lung_Cancer_2026", "dirPath": "..." } }

// 获取工作空间列表
GET /api/workspaces
Response 200:
{
  "code": 200,
  "data": [
    { "id": 1, "name": "Lung_Cancer_2026", "slideCount": 42,
      "doneCount": 10, "updatedAt": "2026-03-17T10:00:00" }
  ]
}

// 重命名工作空间
PUT /api/workspaces/{id}
Body: { "name": "新名称", "description": "..." }

// 删除工作空间（级联删除其中所有图像和标注）
DELETE /api/workspaces/{id}
Response 200: { "code": 200, "data": null }
```

### 6.1 批量上传

#### 6.1.1 单文件 / 多文件上传

```
POST /api/workspaces/{workspaceId}/slides/upload
Content-Type: multipart/form-data
Body: files[] (多个文件二进制), tileSize=256

Response 200:
{
  "code": 200,
  "data": {
    "accepted": 3,           // 识别为支持格式的文件数
    "skipped": 1,            // 不支持格式被跳过的文件数
    "slides": [
      { "id": 1, "fileName": "slide1.svs", "status": "PROCESSING" },
      { "id": 2, "fileName": "img.png",    "status": "PROCESSING" }
    ]
  }
}
```

> 上传采用分块（5 MB/chunk），每个文件独立上传，Spring Boot 落盘后异步触发 Python 微服务预处理。

#### 6.1.2 文件夹上传

前端使用 `<input webkitdirectory>` 读取整个目录树，将其中 PNG/JPG/SVS 文件收集后复用 `6.1.1` 接口批量提交（文件名携带相对路径信息，后端平铺存储）。

#### 6.1.3 压缩包上传

```
POST /api/workspaces/{workspaceId}/slides/upload-archive
Content-Type: multipart/form-data
Body: archive (单个 .zip / .tar.gz / .tar 文件), tileSize=256

Response 202:
{
  "code": 202,
  "data": { "taskId": "unzip-task-xxx", "status": "EXTRACTING" }
}

// 轮询解压+处理进度
GET /api/tasks/{taskId}
Response 200:
{
  "code": 200,
  "data": {
    "status": "DONE",         // EXTRACTING | PROCESSING | DONE | ERROR
    "total": 20,
    "ready": 18,
    "error": 1,
    "skipped": 1
  }
}
```

> 后端解压压缩包 → 递归识别支持格式文件 → 批量写入 `slides` 表 → 逐一触发瓦片预生成。

### 6.2 文件浏览器（工作空间内图像列表）

```
GET /api/workspaces/{workspaceId}/slides?page=1&size=20&keyword=tumor&sortBy=createdAt&order=desc

Response 200:
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "fileName": "slide.svs",
        "format": "SVS",
        "fileSize": 2147483648,
        "width": 100000,
        "height": 80000,
        "status": "READY",
        "annoStatus": "IN_PROGRESS",
        "thumbnailUrl": "/api/slides/1/thumbnail",
        "createdAt": "2026-03-17T10:00:00"
      }
    ],
    "total": 50,
    "current": 1,
    "size": 20
  }
}
```

> 分页由 MyBatis Plus `Page<Slide>` + `IPage` 实现。`annoStatus` 用于前端显示标注进度标识（未标注 / 标注中 / 已完成）。

### 6.3 图像详情与状态查询

```
GET /api/slides/{id}

Response 200:
{
  "code": 200,
  "data": {
    "id": 1,
    "workspaceId": 1,
    "fileName": "slide.svs",
    "status": "READY",          // PENDING | PROCESSING | READY | ERROR
    "annoStatus": "IN_PROGRESS",
    "width": 100000,
    "height": 80000,
    "tileSize": 256,
    "maxZoom": 18,
    "thumbnailUrl": "/api/slides/1/thumbnail"
  }
}
```

### 6.4 DeepZoom 瓦片

```
GET /api/slides/{id}/tiles/{level}/{col}_{row}.png

Response: image/png (256×256)
```

> Spring Boot 直接读取文件系统上的瓦片文件并以流返回（`StreamingResponseBody`）。

### 6.5 标签管理

```
// 获取标签列表
GET /api/slides/{slideId}/labels
Response 200: { "code": 200, "data": [{ "id": 1, "name": "Tumor", "color": "#FF0000", "classIndex": 1 }] }

// 新增标签
POST /api/slides/{slideId}/labels
Body: { "name": "Tumor", "color": "#FF0000" }
Response 200: { "code": 200, "data": { "id": 1, "classIndex": 1 } }

// 删除标签
DELETE /api/labels/{labelId}
Response 200: { "code": 200, "data": null }
```

### 6.6 保存标注

```
POST /api/slides/{slideId}/annotations

Body:
{
  "annotations": [
    {
      "labelId": 1,
      "annoType": "polygon",
      "coordinates": [[x1,y1], [x2,y2], ...]
    },
    {
      "labelId": 2,
      "annoType": "brush",
      "strokes": [{ "points": [[x,y],...], "size": 20 }]
    }
  ]
}

Response 200: { "code": 200, "data": { "saved": 2 } }
```

> Spring Boot Service 将 `coordinates`/`strokes` 序列化为 JSON 字符串，通过 MyBatis Plus 存入 PostgreSQL `JSONB` 字段。

### 6.7 获取标注列表

```
GET /api/slides/{slideId}/annotations

Response 200:
{
  "code": 200,
  "data": [
    {
      "id": 10,
      "labelId": 1,
      "annoType": "polygon",
      "coordinates": [[x1,y1], ...],
      "bboxX": 100, "bboxY": 200, "bboxW": 500, "bboxH": 400,
      "area": 62500.0
    }
  ]
}
```

### 6.8 触发导出

```
POST /api/slides/{slideId}/export
Body: { "patchSize": 256, "includeOriginal": true }

Response 200:
{
  "code": 200,
  "data": { "taskId": 5, "status": "PENDING" }
}

// 轮询任务状态
GET /api/export-tasks/{taskId}
Response 200:
{
  "code": 200,
  "data": { "taskId": 5, "status": "DONE", "downloadUrl": "/api/export-tasks/5/download" }
}

// 下载 ZIP
GET /api/export-tasks/{taskId}/download
Response: application/zip (Content-Disposition: attachment)
```

---

## 7. 数据模型

### 7.1 PostgreSQL 数据库表结构

#### 7.1.0 workspaces（工作空间表）

```sql
CREATE TABLE workspaces (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL UNIQUE,    -- 用户自定义工作空间名称
    description TEXT,
    -- 服务器上该工作空间对应的物理目录（相对于 uploads 根目录）
    dir_path    VARCHAR(512)    NOT NULL UNIQUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
```

#### 7.1.1 slides（图像表）

```sql
CREATE TABLE slides (
    id             BIGSERIAL       PRIMARY KEY,
    workspace_id   BIGINT          NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    file_name      VARCHAR(255)    NOT NULL,
    format         VARCHAR(10)     NOT NULL CHECK (format IN ('PNG', 'JPG', 'SVS')),
    file_path      VARCHAR(512)    NOT NULL,        -- 服务器上的存储路径（含工作空间目录）
    file_size      BIGINT          NOT NULL,        -- 字节数
    width          INTEGER         NOT NULL DEFAULT 0,
    height         INTEGER         NOT NULL DEFAULT 0,
    levels         INTEGER         DEFAULT 1,       -- SVS 金字塔层数
    tile_dir       VARCHAR(512),                    -- DeepZoom 瓦片目录
    anno_status    VARCHAR(20)     NOT NULL DEFAULT 'UNANNOTATED'
                                   CHECK (anno_status IN ('UNANNOTATED','IN_PROGRESS','DONE')),
    status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING','PROCESSING','READY','ERROR')),
    error_msg      TEXT,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_slides_workspace_id ON slides(workspace_id);
```

#### 7.1.2 labels（标签/类别表）

```sql
CREATE TABLE labels (
    id          BIGSERIAL       PRIMARY KEY,
    slide_id    BIGINT          NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    name        VARCHAR(100)    NOT NULL,
    color       CHAR(7)         NOT NULL,           -- 如 #FF0000
    class_index INTEGER         NOT NULL,           -- 掩码像素值（1-based）
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (slide_id, class_index)
);
```

#### 7.1.3 annotations（标注表）

```sql
CREATE TABLE annotations (
    id            BIGSERIAL     PRIMARY KEY,
    slide_id      BIGINT        NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    label_id      BIGINT        NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    anno_type     VARCHAR(20)   NOT NULL CHECK (anno_type IN ('polygon','rectangle','brush')),
    -- 矢量数据以 JSON 存储，兼容 PostgreSQL jsonb 高效查询
    coordinates   JSONB,        -- polygon/rectangle: [[x,y],...]
    strokes       JSONB,        -- brush: [{"points":[[x,y],...],"size":20},...]
    bbox_x        INTEGER,      -- 包围盒，用于快速空间过滤
    bbox_y        INTEGER,
    bbox_w        INTEGER,
    bbox_h        INTEGER,
    area          DOUBLE PRECISION,                 -- 标注面积（像素数）
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_annotations_slide_id ON annotations(slide_id);
CREATE INDEX idx_annotations_label_id ON annotations(label_id);
```

#### 7.1.4 export_tasks（导出任务表）

```sql
CREATE TABLE export_tasks (
    id            BIGSERIAL     PRIMARY KEY,
    slide_id      BIGINT        NOT NULL REFERENCES slides(id) ON DELETE CASCADE,
    patch_size    INTEGER       NOT NULL DEFAULT 256,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','PROCESSING','DONE','ERROR')),
    zip_path      VARCHAR(512),                     -- 生成的 ZIP 文件路径
    error_msg     TEXT,
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    finished_at   TIMESTAMP
);
```

### 7.2 MyBatis Plus 实体类对应关系

| 数据库表 | Java Entity | MyBatis Plus Mapper |
|----------|-------------|---------------------|
| `slides` | `Slide.java` | `SlideMapper` |
| `labels` | `Label.java` | `LabelMapper` |
| `annotations` | `Annotation.java` | `AnnotationMapper` |
| `export_tasks` | `ExportTask.java` | `ExportTaskMapper` |

`coordinates` 和 `strokes` 字段类型为 `JSONB`，在 Java 端映射为 `String`（JSON 字符串），由 Service 层用 Jackson 序列化/反序列化。

### 7.3 前后端 DTO 结构（JSON）

```typescript
// 标注保存请求体
interface SaveAnnotationsRequest {
  labels: {
    id: number;
    name: string;
    color: string;       // "#FF0000"
    classIndex: number;
  }[];
  annotations: {
    id?: number;         // 新建时为空
    labelId: number;
    annoType: "polygon" | "rectangle" | "brush";
    coordinates?: [number, number][];
    strokes?: { points: [number, number][]; size: number }[];
  }[];
}
```

### 7.4 语义掩码规范

- 格式：PNG，单通道（灰度），8-bit
- 像素值：`0` = 背景，`N` = 对应 `labels.class_index`
- 尺寸：与原始图像等宽高（由 Python 微服务生成）

---

## 8. 约束与排除项

### 8.1 支持范围

- 仅支持 **PNG、JPG、SVS** 三种格式（排除 NDPI、BigTIFF、MRXS 等）
- 面向**研究与内部临床使用**，不集成医院 HIS 系统
- 不提供全自动 AI 辅助标注（人工标注为主）

### 8.2 不在当前版本范围内

| 特性 | 说明 |
|------|------|
| 多用户协同标注 | 不支持实时多人同时标注同一张图像 |
| AI 辅助标注 | 不包含自动分割/检测建议功能 |
| 用户认证系统 | MVP 阶段不需要登录 |
| 移动端适配 | 仅面向桌面浏览器 |
| NDPI/BigTIFF 格式 | 超出当前范围 |

---

## 9. 验收标准

### 9.1 功能验收

| 编号 | 测试项 | 通过标准 |
|------|--------|----------|
| TC-01 | 创建 / 重命名 / 删除工作空间 | 操作均正常持久化，删除后级联清除图像和标注 |
| TC-02 | 单文件上传 PNG/JPG/SVS | 三种格式均可正常处理并出现在文件浏览器 |
| TC-03 | 文件夹上传（含子目录） | 递归识别并导入所有支持格式文件，不支持格式自动跳过 |
| TC-04 | 压缩包上传（.zip / .tar.gz） | 解压后正确识别图像，状态轮询可用 |
| TC-05 | 即传即用（上传后立即标注） | 文件 `READY` 后可直接点击进入标注界面 |
| TC-06 | 文件浏览器搜索与排序 | 按文件名搜索结果正确，排序有效 |
| TC-07 | 文件移动到其他工作空间 | 移动后文件归属变更，标注数据保留 |
| TC-08 | SVS 多分辨率缩放导航 | 各层级瓦片加载无缺失，缩放流畅 |
| TC-09 | 小地图导航 | 点击小地图视口正确跳转 |
| TC-10 | 画笔/矩形/多边形工具 | 三种工具均可绘制并保存至 PostgreSQL |
| TC-11 | 撤销/重做 | ≥ 20 步历史正确恢复 |
| TC-12 | 掩码生成正确性 | 掩码像素与标注区域像素级对齐 |
| TC-13 | COCO JSON 格式合法性 | 使用 pycocotools 加载无报错 |
| TC-14 | Patch 提取命名与内容 | 文件名规范，内容与标注区域一致 |
| TC-15 | 一键导出 ZIP 包 | ZIP 结构完整，可正常解压 |

### 9.2 性能验收

| 编号 | 测试项 | 通过标准 |
|------|--------|----------|
| PT-01 | 100k×100k SVS 首帧加载 | ≤ 3 秒 |
| PT-02 | 掩码生成时间 | ≤ 5 秒 |
| PT-03 | 标注交互帧率 | ≥ 30 FPS |

### 9.3 可用性验收

- 邀请 3–5 名用户完成标注任务，收集定性反馈
- 与 LabelMe / QuPath 对比，标注时间缩短 ≥ 30%
- 导出的数据集可直接用于训练小型 UNet，训练流程无报错

---

*本文档基于毕业设计 Proposal（2251773 Yuyao Wang，2025年11月）编写，版本 v1.0。*
