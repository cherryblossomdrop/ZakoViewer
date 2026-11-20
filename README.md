# ZakoViewer

ZakoViewer 是一款基于 Kotlin 与 Jetpack Compose 构建的现代化 Android 视频客户端应用。

## 代码规模统计

本项目遵循现代化 Android 与 Kotlin 规范精心重构，截至当前版本代码规模统计如下：

| 语言 / 类型 | 文件数 | 代码行数 | 主要模块说明 |
| :--- | :---: | :---: | :--- |
| **Kotlin (`.kt`)** | 66 | 17,468 | 业务逻辑、Jetpack Compose 界面、Room 数据库、下载引擎与播放控制器 |
| **C++ / JNI (`.cpp`)** | 1 | 71 | MPV 原生渲染与底层跨语言调用接口 |
| **Gradle 构建 (`.kts`)** | 4 | 242 | 工程与模块级构建脚本、多维度依赖版本管理 |
| **资源配置 (`.xml`)** | 26 | 338 | 清单配置、多主题应用图标、矢量图形资源 |
| **构建配置 (CMake / Props)** | 2 | 25 | CMake 原生链接配置与 Gradle 运行环境参数 |
| **项目开发规范 (`.md`)** | 2 | 34 | 项目开发规范与版本自动化构建准则 (`AGENTS.md`, `GEMINI.md`) |
| **总计** | **101** | **18,178** | **全项目共计约 1.82 万行工程源码** |

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
- **播放器控制与交互增强**
  - 集成 `libmpv`（基于 C++ JNI）底层高性能原生播放器；
  - 支持手势操作：双击播放/暂停、左右滑动进度微调、左侧滑动调节亮度、右侧滑动调节音量；
  - 实时倍速矢量徽标（动态显示 `1.0` / `1.5` / `2.0` 等实时播放倍速）；
  - 支持四角自适应全屏切换与画面比例循环切换；
  - 支持软解 / 硬解模式一键切换、后台画中画与断点记忆续播。
- **双备份模式与流式读写保障**
  - **轻量备份**：不含离线视频时导出精简 `.json` 元数据备份文件；
  - **完整备份**：包含离线视频时打包导出 `.zip` 文件（元数据 + 原始 `.mp4`）；
  - 打包采用零压缩归档（STORED）模式大幅提升导出效率；
  - 导入备份采用 32KB 分块流式落盘与直接流式解析，彻底规避超大视频文件的 `OutOfMemoryError`（OOM）。
- **离线下载与多节点并发**
  - 多 CDN 节点测速与智能路由调度，支持多线程分片断点续传；
  - 启动自检与状态感知：自动扫描下载目录，本地文件缺失自动呈现置灰状态；
  - 封面本地持久化缓存，避免网络临时请求开销；
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

---

## 技术栈

- **开发语言**：Kotlin, C++ (JNI)
- **UI 框架**：Jetpack Compose (Material 3 Expressive)
- **本地存储**：Room Database, Jetpack DataStore Preferences
- **网络与解析**：OkHttp 4, Gson
- **图片加载**：Coil Compose
- **媒体播放**：MPV C++ JNI 绑定

---

## 核心目录结构

```text
app/src/main/
├── cpp/                           # C++ JNI 模块 (CMakeLists.txt, mpv_jni.cpp)
├── kotlin/                        # Kotlin 源码模块
│   ├── core/                      # 核心模块
│   │   ├── cdn/                   # CDN 测速与节点管理
│   │   ├── download/              # 多并发分片下载引擎
│   │   ├── external/              # 外部播放器与下载器调用适配
│   │   ├── icon/                  # 动态桌面图标替换管理
│   │   ├── logger/                # 本地与运行日志组件
│   │   ├── permission/            # 存储及全文件权限处理
│   │   ├── player/                # MPV 播放器封装与手势处理
│   │   ├── search/                # 搜索词法解析器
│   │   └── share/                 # SFW 汉字暗号编解码
│   ├── data/                      # 数据持久化与网络模型
│   │   ├── backup/                # 数据库与多媒体导入导出与备份
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
