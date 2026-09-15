# PixVault

**端侧 AI 相册 / 素材工坊 —— 完全离线运行，用你自己的样本教会它认图**

PixVault 是一款 Android 本地相册与素材管理应用。它把 CLIP 双塔模型完整跑在手机端，用自然语言就能搜图；更重要的是，它内置了一套**端侧自主学习算法**：你只需要为某个标签勾选几张正例图片，它就能自动"学会"这个标签长什么样，并在整个图库中把相似的图片自动归类。

> 全程离线 · 不申请任何权限 · 不联网 · 图片不出设备

---

## 目录

- [一、项目简介](#一项目简介)
- [二、核心亮点：端侧自主学习算法](#二核心亮点端侧自主学习算法)
- [三、功能特性](#三功能特性)
- [四、技术栈](#四技术栈)
- [五、项目结构](#五项目结构)
- [六、快速开始](#六快速开始)
- [七、软件使用说明](#七软件使用说明)
- [八、模型文件说明](#八模型文件说明)
- [九、常见问题](#九常见问题)
- [十、官方客服群](#十官方客服群)
- [十一、开源协议](#十一开源协议)

---

## 一、项目简介

PixVault 解决的是一个很实际的问题：**素材越攒越多，但找起来越来越难**。

传统相册只能按时间、文件名找图；云端相册虽然能自动分类，但要求你把私人照片上传到别人的服务器。PixVault 的选择是：把 AI 推理放在你自己的手机上，让分类规则由你自己定义。

它由两条技术主线构成：

### 1. 多模态理解（CLIP 双塔）

基于 **CLIP ViT-B/32** 的图像编码器与文本编码器，把"图片"和"自然语言"映射到**同一个 512 维向量空间**。因此可以直接比较一段文字和一张图片的相似度——这就是"用一句话搜图"的原理，无需为任何图片写描述、打标签。

### 2. 端侧自主学习（Few-shot 标签学习）

CLIP 本身是通用模型，它知道"这是一只猫"，但不知道"这是我的商品主图"或"这是我拍的夜景素材"。PixVault 的做法不是去微调模型（手机端既慢又耗电），而是引入一层**轻量级小样本学习**：

> 你勾选几张正例 → 应用计算这些图片的"视觉原型" → 用余弦相似度在全库中找出同类图片。

整个"学习"过程只涉及一次向量均值计算，**毫秒级完成、无需训练、完全可解释、随时可重来**。

---

## 二、核心亮点：端侧自主学习算法

这是 PixVault 与普通相册应用最大的区别，也是本项目的技术核心。

### 2.1 一句话概括

> 标签的"识别能力"不是预训练好的，而是**用户用几张示例图片现场教出来的**，并且可以随时通过增删示例、调整阈值来重新训练。

算法本质属于**最近类均值（Nearest Class Mean）**式的原型学习，配合 CLIP 强大的零样本特征提取能力，用极小的成本实现了"自定义分类器"。

### 2.2 算法原理

**第一步：把图片变成单位球面上的点**

图像编码器把任意一张图片映射为 512 维向量，并做 L2 归一化：

```
f_img(image) → v ∈ R^512,   ‖v‖₂ = 1
```

归一化之后，所有图片都落在半径为 1 的超球面上，此时**余弦相似度退化为点积**，计算极快。

**第二步：用正例计算标签原型**

用户为标签 `t` 勾选的图片集合记为 `S_t = {x₁, x₂, …, xₙ}`，先取向量均值，再做一次 L2 归一化，得到该标签的**原型向量**（即类中心）：

```
p_t = normalize( (1/n) · Σ f_img(xᵢ) )
```

这一步对应代码 `VectorUtils.mean()`：先逐维求平均，再归一化。原型向量会被持久化到数据库 `tags.prototypeVector` 字段。

**第三步：全库匹配**

对图库中每一张已生成向量的图片计算与原型的方向一致性：

```
sim = cos(p_t, v) = p_t · v
```

若 `sim ≥ τ_t`，则该图片自动归入标签 `t`（写入 `image_tags`，来源标记为 `AUTO`，并记录相似度分值）。

其中 `τ_t` 是标签的**判定阈值**，默认 `0.30`，用户可在 `0.10 ~ 0.95` 之间自由调节：

- 阈值调低 → 命中更多，但可能混入不相关图片
- 阈值调高 → 结果更纯净，但可能漏掉部分同类图片

**第四步：用户反馈回到第一步**

详情页会根据原型向量反查"推荐标签"（只读、不落库）。用户采纳后即形成新的标注，下次再调整正例集合时，原型向量会被重新计算——**这就是"学习"的闭环**。

### 2.3 学习闭环流程图

```mermaid
flowchart TD
    A[导入图片] --> B[CLIP 图像塔推理]
    B --> C[512 维 L2 归一化向量<br/>写入 images.embedding]
    C --> D{需要新分类?}
    D -->|是| E[标签管理页新建标签]
    E --> F[进入「正例」页<br/>勾选若干张示例图片]
    F --> G[计算原型向量<br/>正例均值 + L2 归一化]
    G --> H[持久化到 tags.prototypeVector]
    H --> I[全库匹配<br/>cos 原型·图片向量 ≥ 阈值]
    I --> J[写入 image_tags<br/>source = AUTO]
    J --> K[文件夹中查看自动归类结果]
    K --> L[详情页查看推荐标签]
    L -->|用户采纳| M[写入 MANUAL 关联]
    M --> F
```

### 2.4 关键实现对照

| 环节 | 关键函数 | 文件位置 |
| --- | --- | --- |
| 图片 → 向量 | `ClipImageEncoder.encodeNormalized` | `app/src/main/java/com/pixvault/ml/ClipImageEncoder.kt` |
| 文本 → 向量 | `ClipTokenizer.encode` → `ClipTextEncoder.encodeNormalized` | `app/src/main/java/com/pixvault/ml/` |
| 编码器生命周期管理 | `EmbeddingService.ensureLoaded / embed` | `app/src/main/java/com/pixvault/data/embedding/EmbeddingService.kt` |
| 计算标签原型 | `TagRepository.computePrototype` | `app/src/main/java/com/pixvault/data/repository/TagRepository.kt` |
| 全库自动匹配 | `TagRepository.autoMatch` | `app/src/main/java/com/pixvault/data/repository/TagRepository.kt` |
| 推荐标签（只读） | `TagRepository.getSimilarTags` | `app/src/main/java/com/pixvault/data/repository/TagRepository.kt` |
| 相似图检索 | `ImageRepository.findSimilar`（Top-20） | `app/src/main/java/com/pixvault/data/repository/ImageRepository.kt` |
| 语义搜图 | `ImageRepository.searchByText`（Top-60） | `app/src/main/java/com/pixvault/data/repository/ImageRepository.kt` |
| 向量工具（余弦/均值/序列化） | `VectorUtils` | `app/src/main/java/com/pixvault/data/util/VectorUtils.kt` |

### 2.5 语义搜图：零样本能力

除了自定义标签，CLIP 的零样本能力也被直接利用。用户在首页切换到「语义」模式后输入自然语言（例如"一只在草地上奔跑的狗"），应用会：

1. 用**自研 CLIP BPE 分词器**把文本编码为 77 长度的 token 序列（含 BOS/EOS 与 padding）；
2. 经文本塔推理得到 512 维文本向量并 L2 归一化；
3. 与库中所有图片向量计算余弦相似度，按分值降序返回 Top-60。

由于图像塔与文本塔来自同一个 CLIP 模型，两者输出处于**同一投影空间**，跨模态比较才成立。项目在开发阶段已用官方 `tokenizers` 实现做过逐 token 全量比对（覆盖中文、重音字符、超长截断等场景），并补齐了 NFC 归一化。

> 分词器为纯 Kotlin 自研实现，不依赖任何 Python 运行时或第三方 NLP 库。

### 2.6 设计取舍与已知局限

开源项目应当坦诚地说明边界。当前版本的设计取舍如下：

**优点**

- **零训练成本**：一次原型计算就是一次向量均值，手机端瞬间完成；
- **完全可解释**：判定依据就是"与示例图片的余弦相似度 + 阈值"，没有黑盒；
- **可逆可重来**：正例集合随时增删，阈值随时调整，重算即可；
- **隐私友好**：模型权重永不改变，用户样本永不出设备。

**已知局限（欢迎 PR 改进）**

- **原型采用算术均值**：对该标签下风格差异很大的多模态素材（例如"我的作品"里既有插画又有摄影）表达能力有限，可升级为多原型 / 聚类中心；
- **暂无负样本机制**：无法主动"排斥"某类图片，只能通过提高阈值或补充正例来间接修正；
- **阈值不自动调优**：需要用户按"命中数量 vs 准确度"手动权衡；
- **全量重算**：每次保存正例都会对全库做一次点积扫描（O(N×D)，D=512）。万级图库无压力，十万级建议引入 ANN 索引（如 HNSW）与增量更新；
- **图像向量仅使用全局特征**：不做主体检测与区域特征，细粒度区分能力受限于 CLIP ViT-B/32 本身。

---

## 三、功能特性

### 图库与检索

- **批量导入**：通过系统 Photo Picker 选择图片，自动拷贝到应用私有目录并读取尺寸、体积、MIME 等元数据；
- **相册网格**：自适应网格浏览，支持多选、批量收藏、批量打标签、批量移入回收站；
- **语义搜图**：用自然语言描述画面内容进行检索（如"蓝色的天空"、"一只橘猫"）；
- **文件名 / 标签过滤**：传统关键字与 `#标签` 混合过滤；
- **相似图检索**：以任意一张图为基准，按余弦相似度排序找出库中相似素材，并显示相似度百分比。

### 自主学习与标签

- **自定义标签**：标签即"文件夹"，可新建、重命名、删除；
- **正例样本管理**：为每个标签勾选若干示例图片；
- **可视化阈值调节**：滑块实时设定判定阈值（0.1 ~ 0.95）；
- **一键保存并匹配**：计算原型向量 → 全库重新归类 → 回显命中数量；
- **推荐标签**：详情页展示模型推理出的候选标签，一键采纳；
- **安全兜底**：重新匹配只会覆盖 `AUTO` 关联，**不会**动用户手动标注（`MANUAL`）的标签。

### 浏览、编辑与整理

- **详情页**：大图预览、左右翻页、重命名、收藏、标签增删、导出到系统相册；
- **全屏查看器**：双指缩放（1x ~ 6x）、拖动、双击 2 倍与复位；
- **图片编辑**：
  - 裁剪：自由裁剪 + 1:1 / 4:3 / 16:9 / 3:4 / 9:16 预设比例；
  - 涂鸦：5 种预设色 + 取色器，3 档笔宽；
  - 文字：8 种颜色、4 种字体、粗体 / 斜体 / 下划线 / 删除线；
  - 撤销 / 重做 / 重置，输出 PNG / JPEG / WEBP 并支持质量调节，可覆盖原图或另存为新图；
- **文件夹视图**：收藏夹、回收站、各标签文件夹一目了然；
- **收藏夹**：常用素材独立归集；
- **回收站**：删除均为软删除（保留原文件与标签关联），支持多选恢复、彻底删除、一键清空。

### 个性化

- **主题**：跟随系统 / 浅色 / 深色三态切换；
- **设置页**：版本与官方客服群入口，群二维码点击可放大查看。

---

## 四、技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Kotlin | 2.2.10 |
| 构建 | Android Gradle Plugin / Gradle Wrapper | 9.3.2 |
| UI | Jetpack Compose (Material 3) | Compose BOM 2026.02.01 |
| 架构 | 单 Activity + Compose 路由 + Repository 分层 | — |
| 本地存储 | Room (KSP) | 2.7.2 |
| 图片加载 | Coil | 3.2.0 |
| 端侧推理 | ONNX Runtime for Android | 1.19.2 |
| AI 模型 | CLIP ViT-B/32（图像塔 + 文本塔，512 维） | — |
| 分词器 | 自研 CLIP BPE（纯 Kotlin，NFC 归一化） | — |
| 最低 / 目标 SDK | minSdk 24 / targetSdk 37 | — |
| ABI | arm64-v8a、x86_64 | — |
| 协程 | kotlinx.coroutines | — |

**架构分层**

```
UI 层（Compose Screen）
    ↓
Repository 层（ImageRepository / TagRepository）← 业务与算法编排
    ↓
数据访问层（Room DAO / Entity）
    ↓
ML 层（ClipImageEncoder / ClipTextEncoder / ClipTokenizer）+ EmbeddingService
```

**权限说明**：应用**未申请任何权限**（无 INTERNET、无存储权限）。图片通过系统 Photo Picker 选择，属于系统授权行为；应用自身全程离线。

---

## 五、项目结构

```
PixVault/
├── app/
│   ├── build.gradle.kts                     # 模块构建配置（SDK、ABI、依赖）
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/                      # 端侧模型与词表
│       │   │   ├── MODEL_README.txt         # 模型放置说明
│       │   │   ├── vocab.json               # CLIP BPE 词表
│       │   │   ├── merges.txt               # CLIP BPE 合并规则
│       │   │   ├── image-encoder.onnx       # 图像塔（需自行下载，见第八节）
│       │   │   └── text-encoder.onnx        # 文本塔（需自行下载，见第八节）
│       │   ├── keepRules/rules.keep
│       │   ├── java/com/pixvault/
│       │   │   ├── MainActivity.kt          # 单 Activity 入口 + 页面路由
│       │   │   ├── PixVaultApp.kt           # Application，数据库与迁移初始化
│       │   │   ├── ml/                      # ★ 端侧模型层
│       │   │   │   ├── ClipImageEncoder.kt  #   图像塔 ONNX 推理 + 预处理
│       │   │   │   ├── ClipTextEncoder.kt   #   文本塔 ONNX 推理
│       │   │   │   └── ClipTokenizer.kt     #   自研 CLIP BPE 分词器
│       │   │   ├── data/
│       │   │   │   ├── db/                  # Room 数据库
│       │   │   │   │   ├── AppDatabase.kt   #   版本与 Migration
│       │   │   │   │   ├── dao/             #   ImageDao / TagDao / ImageTagDao
│       │   │   │   │   └── entity/          #   images / tags / image_tags 表
│       │   │   │   ├── embedding/
│       │   │   │   │   └── EmbeddingService.kt   # 编码器加载与向量计算
│       │   │   │   ├── repository/          # ★ 学习算法编排层
│       │   │   │   │   ├── ImageRepository.kt    # 相似图 / 语义搜图 / 回收站
│       │   │   │   │   └── TagRepository.kt      # 原型计算 / 全库匹配 / 推荐
│       │   │   │   ├── importer/ImageImporter.kt # 导入与元数据读取
│       │   │   │   ├── processor/ImageProcessor.kt # 裁剪/涂鸦/文字/导出
│       │   │   │   └── util/VectorUtils.kt  # 余弦 / 均值 / 归一化 / 序列化
│       │   │   └── ui/
│       │   │       ├── screen/              # 13 个页面（见下）
│       │   │       └── theme/               # 颜色、字体、主题模式
│       │   └── res/                         # 图标、字符串、主题、群二维码
│       ├── test/                            # 单元测试
│       └── androidTest/                     # 仪器测试
├── gradle/
│   ├── libs.versions.toml                   # 依赖版本目录
│   └── wrapper/                             # Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── LICENSE
└── README.md
```

**页面一览（`ui/screen/`）**

| 文件 | 页面职责 |
| --- | --- |
| `HomeScreen.kt` | 相册主网格、导入、筛选、语义搜图、多选批量操作 |
| `DetailScreen.kt` | 图片详情、改名、收藏、标签管理、推荐标签、找相似、导出 |
| `ImageViewerScreen.kt` | 全屏查看，缩放与拖动 |
| `ImageEditorScreen.kt` | 裁剪 / 涂鸦 / 文字编辑与导出 |
| `TagManagerScreen.kt` | 标签列表、新建、删除、进入正例 |
| `PositiveSelectScreen.kt` | **正例选择与阈值调节（学习入口）** |
| `SimilarImagesScreen.kt` | 相似图结果展示 |
| `FolderListScreen.kt` | 文件夹总览（收藏、回收站、标签） |
| `FolderImagesScreen.kt` | 单个文件夹内图片 |
| `FavoritesScreen.kt` | 收藏夹 |
| `TrashScreen.kt` | 回收站（恢复 / 彻底删除 / 清空） |
| `SettingsScreen.kt` | 主题切换、官方客服群与二维码 |
| `GroupBuyScreen.kt` | 周边团购信息页 |

---

## 六、快速开始

### 环境要求

- Android Studio（建议较新版本，支持 AGP 9.x）
- JDK 17 及以上（Android Studio 自带 JBR 即可）
- Android SDK Platform 37
- 一台 Android 7.0（API 24）及以上的设备，或对应 API 的模拟器

### 构建步骤

1. **克隆仓库**

   ```bash
   git clone <你的仓库地址>
   cd PixVault
   ```

2. **下载端侧模型**（仓库不包含，共约 146 MB，见[第八节](#八模型文件说明)）

   把两个 `.onnx` 文件放入：

   ```
   app/src/main/assets/image-encoder.onnx
   app/src/main/assets/text-encoder.onnx
   ```

3. **打开并运行**

   用 Android Studio 打开工程，等待 Gradle 同步完成后直接运行 `app` 配置。

   或使用命令行：

   ```bash
   # Windows
   gradlew.bat assembleDebug

   # macOS / Linux
   ./gradlew assembleDebug
   ```

   > `local.properties` 已被 `.gitignore` 排除，Android Studio 会自动生成；命令行构建需确保已设置 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）。

### 构建提示

- 首次运行会按需把模型拷贝到应用私有目录，届时需要等待数秒；
- 图像塔在首次导入图片时加载，文本塔仅在首次使用「语义搜图」时**懒加载**；
- `release` 构建当前复用 debug 签名，仅供本地自测，正式发布请替换为自己的签名配置。

---

## 七、软件使用说明

### 1. 导入图片

首页点击**导入**按钮 → 系统图片选择器（可多选）→ 确认后应用会把图片拷贝到私有目录并自动计算图像向量，进度以"计算特征 n/N"显示。若在选择时同时指定标签，则这些图片会立刻归入该标签。

> 建议首次多导入一些图片，向量计算是后续所有检索与学习的基础。

### 2. 浏览与检索

- **默认模式**：输入关键字过滤文件名，或输入 `#标签名` 过滤标签；
- **语义模式**：点击搜索框下方的模式切换按钮，输入自然语言描述（如"雪地里的脚印"），点击「搜索」，应用会按语义相似度返回结果。

### 3. 让标签"学会"认图（核心玩法）

这是 PixVault 最具特色的功能，四步即可拥有一个专属分类器：

1. **新建标签**
   首页 → **标签** → 新建标签，例如"夜景"。

2. **挑选正例**
   在标签列表中点击该标签右侧的**正例**入口，进入图片网格，勾选 3 ~ 10 张最能代表该标签的图片。

   > 样本原则：**少而准**。正例只放"绝对属于该标签"的图片，宁缺毋滥。

3. **调节阈值并保存**
   拖动阈值滑块（默认 0.30），点击**保存并匹配**：

   - 应用会计算这些正例的原型向量；
   - 然后扫描全库，把相似度不低于阈值的图片自动归入该标签（来源标记为自动）；
   - 完成后会提示命中数量，例如"完成：命中 27 张（阈值 0.30）"。

4. **检查与迭代**
   进入文件夹查看自动归类结果：

   - **漏了** → 把漏掉的图片补进正例，或适当调低阈值；
   - **混入了不相关的** → 提高阈值，或移除不够典型的正例。

   重复第 2 ~ 4 步，分类效果会越来越准。手动打的标签不会被自动匹配覆盖，可以放心使用。

### 4. 查看与整理

- **相似图**：详情页点击「找相似」，按相似度查看库中相近素材；
- **推荐标签**：详情页会列出模型推荐的标签，点击即可采纳为手动标签；
- **收藏**：详情页或网格多选中点击收藏；
- **重命名 / 导出**：详情页可改名，也可导出回系统相册。

### 5. 编辑图片

详情页进入编辑，底部工具栏提供四种模式：

| 模式 | 能力 |
| --- | --- |
| 查看 | 手势缩放、拖动 |
| 裁剪 | 自由裁剪，或使用 1:1 / 4:3 / 16:9 / 3:4 / 9:16 预设 |
| 涂鸦 | 5 种预设色 + 取色器，3 档笔宽 |
| 文字 | 8 色、4 字体，粗体 / 斜体 / 下划线 / 删除线 |

支持撤销、重做、重置；导出时可选择 PNG / JPEG / WEBP、调节质量，并决定覆盖原图或另存为新图。

### 6. 文件夹、收藏与回收站

- **文件夹**页汇总了收藏夹、回收站与所有标签文件夹；
- 删除图片会先**移入回收站**，原文件与标签关联都会保留；
- 在回收站中可以多选**恢复**、**彻底删除**，或一键**清空回收站**（此操作不可撤销）。

### 7. 设置

- 主题：跟随系统 / 浅色 / 深色；
- 官方客服群：显示群号与群二维码，点击二维码可放大查看，方便扫码进群。

---

## 八、模型文件说明

仓库为控制体积**未包含**模型权重（`*.onnx` 已被 `.gitignore` 排除），词表文件已随仓库提供。

| 文件 | 体积 | 必需性 | 说明 |
| --- | --- | --- | --- |
| `image-encoder.onnx` | 约 85 MB | **必需** | 图像塔，负责生成图片向量；缺失则无法导入/检索 |
| `text-encoder.onnx` | 约 61.5 MB | 语义搜图必需 | 文本塔（量化版），仅在使用语义搜图时懒加载 |
| `vocab.json` | 约 862 KB | 已包含 | CLIP BPE 词表 |
| `merges.txt` | 约 525 KB | 已包含 | CLIP BPE 合并规则 |

使用 HuggingFace 仓库 `Xenova/clip-vit-base-patch32` 的 ONNX 权重，按下列对应关系重命名后放入 `app/src/main/assets/`：

| 下载文件 | 重命名为 |
| --- | --- |
| `onnx/model.onnx` | `image-encoder.onnx` |
| `onnx/text_model_quantized.onnx` | `text-encoder.onnx` |
| `vocab.json` | `vocab.json`（仓库已包含，可跳过） |
| `merges.txt` | `merges.txt`（仓库已包含，可跳过） |

若访问 HuggingFace 主站有困难，可使用镜像域名替换 `huggingface.co`：

```
https://hf-mirror.com/Xenova/clip-vit-base-patch32/resolve/main/onnx/model.onnx
https://hf-mirror.com/Xenova/clip-vit-base-patch32/resolve/main/onnx/text_model_quantized.onnx
```

模型接口约定（`MODEL_README.txt` 中有同样说明）：

- **图像塔**：输入 `float32`，NCHW 布局 `[1, 3, H, W]`（通常 224×224）；输出 `float32` `[1, 512]`，输出名 `image_embeds`；
- **文本塔**：输入 `int64` `[batch, 77]`（`input_ids`）；输出 `float32` `[batch, 512]`，输出名 `text_embeds`；
- 两塔必须来自同一 CLIP 模型，输出需处于同一投影空间（本项目为 512 维）；
- 支持 FP32 / FP16 / INT8 量化模型，代码会从 ONNX 输入 shape 动态读取分辨率。

> **想直接提交模型？** 请使用 [Git LFS](https://git-lfs.com/) 管理 `*.onnx`，或将其上传到 GitHub Releases，而不是以普通文件提交（单个文件超过 100 MB 会被 GitHub 拒收）。

---

## 九、常见问题

**Q：为什么仓库里没有模型文件？**
A：两个 ONNX 模型合计约 146 MB，超过 GitHub 单文件 100 MB 限制。请按第八节自行下载放入 `app/src/main/assets/`。

**Q：应用需要联网吗？会不会上传我的照片？**
A：完全不需要。应用未声明 `INTERNET` 权限，也不申请存储权限，所有推理都在本机完成。

**Q：为什么自动匹配结果不理想？**
A：优先检查正例质量。正例要"纯"——只包含明确属于该标签的图片；其次调整阈值，先降低阈值看命中范围，再逐步调高筛选质量。

**Q：手动打的标签会被自动匹配覆盖吗？**
A：不会。自动匹配只写入和覆盖来源为 `AUTO` 的关联，手动标注（`MANUAL`）始终保留。

**Q：图片很多时会不会很慢？**
A：向量计算是导入时一次性完成的，之后检索只是点积比较。当前实现为全库扫描，万级图库体验良好；更大规模建议引入 ANN 索引。

**Q：支持哪些 CPU 架构？**
A：当前打包 `arm64-v8a` 与 `x86_64`，覆盖绝大多数现代真机与模拟器。

---

## 十、官方客服群

使用中遇到问题、想提建议或反馈 Bug，欢迎加入官方 QQ 客服群：

**QQ 群：305402575**

也可以在应用的「设置」页查看群二维码，点击可放大后扫码进群。

> 提交 Issue 时，建议附上设备型号、Android 版本与问题复现步骤，便于快速定位。

---

## 十一、开源协议

本项目基于 **Apache License 2.0** 开源，详见 [LICENSE](LICENSE)。

你可以自由地使用、修改、分发本项目（包括商业用途），但需保留版权声明与协议文本。

使用的第三方模型与资源版权归其各自所有者所有：

- CLIP 模型权重：遵循其原始发布仓库的许可条款（`Xenova/clip-vit-base-patch32`）；
- ONNX Runtime：MIT License；
- AndroidX / Jetpack Compose / Coil 等：Apache License 2.0。

**免责声明**：AI 推理结果仅为辅助参考，可能存在误判；请在执行删除、覆盖等不可逆操作前自行确认。应用内「周边团购」页面为第三方商品信息，与本开源项目无隶属关系。