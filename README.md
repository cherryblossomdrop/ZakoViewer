# ZakoViewer

ZakoViewer 是一款基于 Kotlin 与 Jetpack Compose 构建的现代化、高性能 Android 视频客户端应用。

## 代码规模统计

本项目遵循现代化 Android 与 Kotlin 规范精心重构，截至最新版本（v1.2.28）代码规模统计如下：

| 语言 / 类型 | 文件数 | 代码行数 | 主要模块说明 |
| :--- | :---: | :---: | :--- |
| **Kotlin (`.kt`)** | 68 | 20,429 | 业务逻辑、Jetpack Compose 界面、Room 数据库、下载引擎、播放控制与公共存储适配 |
| **C++ / JNI (`.cpp`)** | 1 | 561 | MPV 原生渲染与底层跨语言调用接口、JNI 线程锁与异步状态管理 |
| **Gradle 构建 (`.kts`)** | 3 | 146 | 工程与模块级构建脚本、多维度依赖版本管理 |
| **资源配置 (`.xml`)** | 26 | 338 | 清单配置、多主题应用图标、矢量图形资源 |
| **构建配置 (CMake / Props)** | 2 | 30 | CMake 原生链接配置与 Gradle 运行环境参数 |
| **项目开发规范 (`.md`)** | 2 | 18 | 项目开发规范与版本自动化构建准则 (`AGENTS.md`, `GEMINI.md`) |
| **总计** | **102** | **21,522** | **全项目共计约 2.15 万行工程源码** |

---

## 功能特性

- **Material 3 Expressive 界面与主题系统**
  - 全面采用沉浸式边到边（Edge-to-Edge）排版与状态栏动态适配；
  - 支持系统莫奈（Monet）动态取色开关；
  - 关闭动态取色后，支持自定义主题配色（包括预设紫色 `#686BDD`、浅绿色、淡黄色以及自定义调色板）；
  - 支持应用桌面图标颜色跟随主题色切换（重启应用生效）。
- **平板与多端自适应布局**
  - 平板横屏左右双栏自适应黄金排版（左侧播放与元数据交互，右侧独立滚动推荐视频网格）；
  - 侧边栏（NavigationRail）与底部栏（NavigationBar）自适应切换；
  - 视频播放卡片上边缘自然贴合系统状态栏，无多余顶部间隔。
- **MPV 底层原生播放引擎与稳定性保障**
  - 集成 `libmpv`（纯血 64 位 ARM64-v8a 原生编译架构），硬解性能卓越；
  - 底层 JNI 引入互斥锁（Mutex）与异步 Seek 保护机制，彻底消除引擎切换、初始 Seek 与 Surface 竞争导致的 SIGSEGV 崩溃与黑屏；
  - 规避重复 VO（视频输出）初始化，强制确保 `vo=gpu`、硬件加速解码（hwdec）与 Surface 准确绑定，杜绝主线程阻塞 ANR；
  - 支持手势操作：双击播放/暂停、左右滑动进度微调、左侧滑动调节亮度、右侧滑动调节音量；
  - 实时倍速矢量徽标（动态显示 `1.0` / `1.5` / `2.0` 等实时播放倍速）；
  - 支持四角自适应全屏切换、画面比例循环调整、软/硬解模式无缝切换与后台画中画断点记忆续播；
  - 内置多线程专用本地反向代理，支持 Range 流式 Seek 与智能节点重试。
- **多维数据备份与恢复系统（全流程体验与安全防护升级）**
  - **公共目录免 Root 直出**：
    - 备份文件统一直接导出至公共存储 `Download/ZakoViewer/Backups/`（格式为 `ZakoViewer_Backup_yyyyMMdd_HHmmss.zip` 或 `.json`）；
    - Android 10+ 自动适配 `MediaStore.Downloads`，Android 9 及以下回退公共外部存储，非 Root 用户在系统文件管理器中立即可见；
    - 界面常驻提示导出保存目录，支持点击一键复制路径；导出完成弹出结果对话框，支持一键系统分享与路径复制。
  - **不可误触关闭的进度弹窗 & 屏幕常亮保护**：
    - 导入恢复及包含视频的大备份包导出期间，使用不可取消对话框（屏蔽返回键与蒙层点击），防止意外中断破坏本地数据库；
    - 任务期间自动请求并保持屏幕常亮（`FLAG_KEEP_SCREEN_ON`），避免大文件处理中设备锁屏休眠打断感知，任务结束后自动释放；
    - 常驻明确的防退出与耗时耐心等待提醒文案。
  - **分阶段实时精准进度反馈**：
    - **阶段 1（读取准备）**：流式复制至临时文件时实时计算已读取大小与百分比（80ms 限流平滑回调）；
    - **阶段 2（解压还原）**：预统计归档中的封面与视频条目总数，分步呈现「正在恢复视频 (2/6)...」及精确百分比进度条；
    - **阶段 3（数据合并）**：写入收藏夹树结构、观看历史、稍后再看与离线视频映射，自动执行本地文件校验与缺失封面异步补齐。
  - **结果汇总反馈与临时缓存强清理**：
    - 恢复完成后弹窗结构化汇总：收藏夹目录数、收藏视频总数、历史记录数、稍后再看数、还原视频文件数及封面状态；
    - 临时解压文件 `temp_restore_file` 在 `try/finally` 中百分之百彻底安全删除，启动时二次兜底清理，杜绝缓存暗中暴涨。
- **精细化封面缓存与离线存储管理**
  - **严格限制封面本地持久化范围**：仅对收藏夹、已下载视频、稍后再看、历史记录 4 个核心业务场景持久化保存封面；首页推荐流与搜索结果仅使用临时图片内存/磁盘加载缓存，防止海量浏览导致存储与备份包无限膨胀；
  - **下载目录修改与安全流式迁移**：支持自由修改已下载视频目录，目录变更后自动触发流式复制迁移、文件大小一致性校验并安全删除源文件，附带完备的 WARN/INFO 迁移排查日志；
  - **精准缓存统计**：应用内缓存大小统计与清理严格排除已下载视频目录与临时恢复文件。
- **离线下载与多节点并发**
  - 多 CDN 节点测速与智能路由调度，支持多线程分片断点续传；
  - 启动自检与状态感知：自动扫描下载目录，本地文件缺失自动呈现置灰状态；
  - 支持调用系统及外部下载器（如 1DM / ADM）与外部播放器。
- **无限层级收藏夹与索引管理**
  - 无限层级树形文件夹管理，支持批量多选移动、复制、重命名与导出；
  - 内置本地文件夹树形选择组件（`FolderTreeDialog` / `FolderBrowserDialog`）。
- **多模式浏览与复合搜索**
  - 支持瀑布流与分页规整网格双排布模式平滑切换；
  - 复合搜索词法解析，支持分类、番号、演员、片商及暗号直接解析；
  - 搜索历史面板支持多选与批量清理管理。
- **隐私防社死保护与 SFW 汉字暗号分享**
  - 视频封面缩略图默认开启模糊遮罩，支持在「我的 → 设置 → 隐私」页面随时一键开关；
  - 详情页短按分享按钮：生成纯自然中文无敏感词暗号，在搜索框输入回车即可直达对应内容；
  - 彩蛋机制：在「我的 → 关于 APP」中连续点击版本号 5 次可解锁隐藏开关，开启后长按分享图标可直接复制完整视频链接。
- **结构化日志管理与原子清空**
  - 日志文件一键导出至公共存储目录 `Download/ZakoViewer/Logs/`，非 Root 用户直接可读；
  - 日志清空按钮在线程锁保护下，原子化同步清空内存记录、UI 状态，彻底截断磁盘持久化文件 `zako_persistent.log`（0 字节重置），并清理历史导出缓存。

---

## 技术栈

- **开发语言**：Kotlin, C++ (JNI)
- **UI 框架**：Jetpack Compose (Material 3 Expressive)
- **本地存储**：Room Database, Jetpack DataStore Preferences
- **网络与解析**：OkHttp 4, Gson
- **图片加载**：Coil Compose
- **媒体播放**：MPV C++ JNI 绑定 (ARM64-v8a)
- **存储架构**：Android MediaStore API (Scoped Storage), SAF

---

## 核心目录结构

```text
app/src/main/
├── cpp/                           # C++ JNI 模块 (CMakeLists.txt, mpv_jni.cpp)
├── kotlin/                        # Kotlin 源码模块
│   ├── core/                      # 核心模块
│   │   ├── cdn/                   # CDN 测速与节点管理
│   │   ├── cover/                 # 核心场景封面持久化与按需补全 (CoverCacheManager)
│   │   ├── download/              # 多并发分片下载引擎
│   │   ├── external/              # 外部播放器与下载器调用适配
│   │   ├── icon/                  # 动态桌面图标替换管理
│   │   ├── logger/                # 本地与运行日志组件及原子清空
│   │   ├── permission/            # 存储及全文件权限处理
│   │   ├── player/                # MPV 播放器封装、JNI 线程锁与手势处理
│   │   ├── search/                # 搜索词法解析器
│   │   ├── share/                 # SFW 汉字暗号编解码
│   │   └── storage/               # 公共 Downloads 存储适配 (PublicStorageHelper)
│   ├── data/                      # 数据持久化与网络模型
│   │   ├── backup/                # 数据库与多媒体导入导出、分阶段进度与恢复管理器
│   │   ├── local/                 # Room 数据库、DAO 与数据实体
│   │   ├── repository/            # 数据仓库层
│   │   └── source/                # 数据源与网络交互
│   └── ui/                        # Compose UI 表现层
│       ├── components/            # 通用组件（文件选择树、弹窗、倍速徽标等）
│       ├── navigation/            # 页面导航图与自适应侧边栏
│       ├── screens/               # 各功能页面（Home, Detail, Player, Profile 等）
│       └── theme/                 # 主题配色、动态取色与字体排版
└── res/                           # 资源目录（矢量图、多主题应用图标、多语言字符）
```

---

## 本地编译与构建指南

### 1. 环境要求

- JDK 17
- Android SDK (compileSdk 34, minSdk 26, targetSdk 34)
- Gradle 8.10+（建议使用项目自带的 Gradle Wrapper）
- Android NDK（支持 C++ 模块编译）

### 2. 配置文件说明

本项目采取配置解耦策略，本地环境与私有密钥不进入版本控制：

#### Android SDK 配置 (`local.properties`)
在根目录确认或创建 `local.properties`：
```properties
sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```

#### Release 签名配置 (`keystore.properties`)
如需构建 Release 安装包，在根目录下创建 `keystore.properties`（已加入 `.gitignore`）：
```properties
storeFile=app/zakoviewer-release.jks
storePassword=your_keystore_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

> 说明：若未配置 `keystore.properties`，构建系统会自动跳过 Release 签名，不影响 Debug 版本的本地编译与调试。

### 3. 构建命令

```bash
# 验证 Debug 与 Release 双版本构建
.\gradlew.bat assemble --no-daemon --console=plain

# 单独编译 Debug APK
.\gradlew.bat assembleDebug

# 单独编译 Release APK (需先配置 keystore.properties)
.\gradlew.bat assembleRelease
```

---

## 开源许可

本项目核心播放模块及底层 JNI 架构衍生自 GPLv3 开源项目（NextPlayerCloud / NeoPlayer），根据 GNU 强互惠（Copyleft）许可证要求，本项目整体遵循 [GNU General Public License v3.0 (GPLv3)](LICENSE) 协议开源。
