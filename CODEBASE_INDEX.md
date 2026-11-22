# ZakoViewer 全局代码索引与架构映射 (Codebase Index)

> **版本定位**：截至最新构建（`v1.2.37`），全项目核心工程源码共计 **86** 个文件，累计 **23,369** 行代码。  
> **协作准则**：在排查故障、分析逻辑链路或规划功能改动前，**必须优先查阅本索引**快速定位关键文件。每次新增、删除、重命名或修改代码文件后，必须同步维护受影响条目的文件路径、精确行数与核心实现方式。

---

## 目录索引导航
1. [全局应用中枢与系统配置](#1-全局应用中枢与系统配置)
2. [核心架构与基建驱动 (core/)](#2-核心架构与基建驱动-core)
3. [原生 C/C++ 渲染管线 (cpp/)](#3-原生-cc-渲染管线-cpp)
4. [数据持久化、仓储与网络数据源 (data/)](#4-数据持久化仓储与网络数据源-data)
5. [UI 设计令牌与主题体系 (ui/theme/)](#5-ui-设计令牌与主题体系-uitheme)
6. [UI 公共复用组件 (ui/components/)](#6-ui-公共复用组件-uicomponents)
7. [UI 导航与路由拓扑 (ui/navigation/)](#7-ui-导航与路由拓扑-uinavigation)
8. [UI 业务视窗与 ViewModel (ui/screens/)](#8-ui-业务视窗与-viewmodel-uiscreens)
9. [工程构建与 CI/CD 自动化流水线](#9-工程构建与-cicd-自动化流水线)

---

## 1. 全局应用中枢与系统配置

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/AndroidManifest.xml` | 135 | 应用全局清单，注册 Activity、前台服务、权限与文件 Provider | 声明媒体读写与存储权限、文件共享 FileProvider 及 DownloadService |
| `app/src/main/kotlin/moe/zako/viewer/ZakoApplication.kt` | 139 | 应用级 Application 单例，初始化全局日志、崩溃兜底、Coil 图像加载及核心数据仓储 | 继承 `Application` 实现 `ImageLoaderFactory`，提供数据库与仓储 lazy 门面，启动预建根收藏夹与封面迁移 |
| `app/src/main/kotlin/moe/zako/viewer/MainActivity.kt` | 87 | 单 Activity 承载宿主，处理窗口全屏边到边（Edge-to-Edge）与系统栏视觉沉浸 | 基于 `ComponentActivity`，通过 `enableEdgeToEdge()` 配置状态栏/导航栏透明度，装载 `ZakoNavHost` 根组合项 |

---

## 2. 核心架构与基建驱动 (`core/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSmartRouter.kt` | 66 | 智能 CDN 线路路由与测速结果解析仲裁 | 维护多节点元数据，结合 `CdnSpeedTester` 返回的 TTFB 和带宽动态评分并产出最佳线路 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSpeedTester.kt` | 153 | 多路 CDN 节点并发网络探测与测速引擎 | 基于 OkHttp 协程并发发起 Range 请求，探测 TCP/TLS 握手延迟与突发下行速率 |
| `app/src/main/kotlin/moe/zako/viewer/core/cover/CoverCacheManager.kt` | 397 | 视频封面本地持久化缓存、离线自愈与按需加载中枢 | 本地文件 Hash 寻址存储，封装 Coil `ImageRequest` 拦截器，支持旧版离线封面静默自愈 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadManager.kt` | 746 | 离线下载全生命周期管理中枢，协调任务调度、断点续传与存储目录迁移 | 单例维护下载任务状态机（Flow 响应式通知），协调 `DownloadDao` 与本地文件物理 I/O |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadService.kt` | 51 | 离线下载前台服务，保障后台下载稳定不被系统杀进程 | 继承 Android `Service`，绑定前台通知通道并实时显示下载进度与速率通知 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/MultiNodeDownloader.kt` | 353 | 多节点并发分片断点续传下载底层实现 | 基于 OkHttp 流式拉取 HTTP Range 分片，多线程并发校验并合并写入磁盘目标文件 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/AppPickerHelper.kt` | 122 | 外部应用与播放器匹配助手 | 通过 `PackageManager` 查询匹配支持视频播放 Intent 的系统及第三方应用列表 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/ExternalLauncher.kt` | 96 | 唤起外部第三方播放器（如 VLC、MX Player）或系统相册应用 | 构造带播放 MIME 类型与授权 URI（FileProvider）的 `ACTION_VIEW` 显式/隐式 Intent |
| `app/src/main/kotlin/moe/zako/viewer/core/icon/AppIconManager.kt` | 79 | 桌面动态图标别名热切换器 | 操纵 `PackageManager.setComponentEnabledSetting` 切换 Manifest 中的 `<activity-alias>` |
| `app/src/main/kotlin/moe/zako/viewer/core/logger/ZakoLog.kt` | 465 | 结构化诊断日志框架，提供内存缓冲、磁盘滚动落盘与系统信息采集 | 单例封装双端日志输出，支持日志级别过滤、公共下载目录原子导出与崩溃诊断头组装 |
| `app/src/main/kotlin/moe/zako/viewer/core/permission/StoragePermissionHelper.kt` | 50 | 外部存储与所有文件访问权限（MANAGE_EXTERNAL_STORAGE）检查适配器 | 区分 Android 11+（`Environment.isExternalStorageManager`）与低版本运行时权限检查与设置跳转 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVLib.kt` | 152 | libmpv 原生 JNI 跨语言调用桥梁 | 声明 `create`、`init`、`destroy`、`command`、`setOptionString` 等 external JNI 原生句柄方法 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVView.kt` | 129 | libmpv 原生视频 SurfaceView 渲染承载组件 | 继承 `SurfaceView` 实现 `SurfaceHolder.Callback`，协同原生 OpenGL/EGL 绘制表面生命周期 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MultiNodeStreamBuffer.kt` | 620 | 流媒体本地微型代理网关，提供分片缓冲与边下边播 | 基于嵌入式 Socket 建立本地代理，处理 MPV 发起的 Range 请求并并发拉取远端源 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/ZakoPlayer.kt` | 964 | 播放器核心业务封装门面，统筹状态流转、播放模式与手势调控 | 维护 MPV 原生状态流（StateFlow），封装起播、暂停、平滑 Seek、画幅比例与音频管线调度 |
| `app/src/main/kotlin/moe/zako/viewer/core/search/SearchQueryParser.kt` | 92 | 复合搜索词法解析引擎，支持高级布尔语法与暗号解析 | 词法拆解查询字串为 Tag/关键词过滤器，自动嗅探并隔离提取 SFW 汉字隐写特征码 |
| `app/src/main/kotlin/moe/zako/viewer/core/share/ShareCipherUtil.kt` | 79 | 语义级 SFW 汉字隐写编解码分享协议工具 | 建立 Base64/Hex 与自然汉字字库的高维双向映射表，实现零外链敏感拦截的暗号编解码 |
| `app/src/main/kotlin/moe/zako/viewer/core/storage/PublicStorageHelper.kt` | 96 | 分区存储公共空间直出与 MediaStore 写入适配器 | 遵循 Android 10+ Scoped Storage，通过 `MediaStore.Downloads` 写入备份包与导出日志 |

---

## 3. 原生 C/C++ 渲染管线 (`cpp/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/cpp/CMakeLists.txt` | 25 | CMake 原生编译构建配置脚本 | 声明 `mpv_jni` 共享库目标，链接预编译 `libmpv`、`libandroid`、`liblog` 与 EGL 渲染库 |
| `app/src/main/cpp/mpv_jni.cpp` | 561 | MPV JNI 桥接层与互斥锁安全同步实现 | C++17 实现 JNI 接口，采用 `std::mutex` 与条件变量保障 Surface 绑定、渲染调用与销毁线程安全 |

---

## 4. 数据持久化、仓储与网络数据源 (`data/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/data/backup/BackupRestoreManager.kt` | 535 | 跨版本数据库全量导出与导入恢复管理器 | 流式压缩/解压 ZIP 容器，校验版本元数据，原子合并/覆写 Room 表并保持屏幕常亮唤醒锁 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/ZakoDatabase.kt` | 81 | Room 主数据库入口与单例管理 | 继承 `RoomDatabase`，聚合 Favorite、History、WatchLater、Download 数据表并管理升级迁移 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/FavoriteDao.kt` | 87 | 树形收藏夹与收藏条目数据库数据访问对象 (DAO) | 定义 Room 查询、层级路径递归遍历、批量复制/移动及唯一根收藏夹的 SQL 事务操作 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/OtherDaos.kt` | 93 | 历史记录、稍后再看、搜索词历史与离线下载 DAO 集合 | 包含 `WatchHistoryDao`、`WatchLaterDao`（支持批量移除）、`SearchHistoryDao`、`DownloadDao` 的响应式 SQL 接口 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/FavoriteEntities.kt` | 44 | 树形收藏夹目录实体与收藏条目实体定义 | Room 实体 `FavoriteFolderEntity`（自关联 `parentId`）与 `FavoriteItemEntity` |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/LocalEntities.kt` | 76 | 观看历史、稍后再看、搜索历史与下载任务 Room 实体集合 | 定义 `WatchHistoryEntity`、`WatchLaterEntity`、`SearchHistoryEntity`、`DownloadEntity` 数据结构 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/FavoriteRepository.kt` | 148 | 收藏体系业务仓库，统一维护无限级收藏夹拓扑与封面保活 | 封装 `FavoriteDao` 异步操作，提供创建/重命名/删除文件夹及条目增删与封面持久化触发 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/HistoryAndSettingsRepositories.kt` | 343 | 历史记录、稍后再看、搜索记录与全局 DataStore 配置仓储集合 | 整合 `HistoryRepository`（支持批量移出稍后再看）、`SearchHistoryRepository` 及基于 Preferences DataStore 的设置管理 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosApiClient.kt` | 192 | 视频源服务端网络请求客户端 | 封装 OkHttp 访问远端列表、检索及详情 API，支持智能线路备选与 JSON 反序列化 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosCrypto.kt` | 49 | 远端数据源响应报文加解密算法工具 | 实现视频流地址与加密元数据报文的对称解密算法 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosModels.kt` | 272 | 远端服务业务数据传输模型 (DTO) 集合 | 定义视频条目 `CosVideoItem`、详情 `CosVideoDetail`、评论、标签及分页响应模型 |

---

## 5. UI 设计令牌与主题体系 (`ui/theme/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/AppIcons.kt` | 75 | 全局统一图标 Design Token 门面 | 语义化聚合多选、收藏、播放、删除、详情、文件及设置系统操作图标，权威对齐 Checklist 多选图标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Color.kt` | 413 | 全局色彩拓扑、调色板预设与莫奈动态取色方案 | 定义多种内置主题色盘（经典紫、自然绿、暖阳黄等）及暗色/亮色 ColorScheme 语义 token |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MenuTokens.kt` | 37 | 上下文菜单与底部操作栏样式 Design Token | 集中规范菜单容器与项圆角、标高、内边距、图标规格、文字样式及动态危险色 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Theme.kt` | 45 | Material 3 主题容器组合项与 LocalComposition 注入 | 提供 `ZakoViewerTheme`，将动态色彩方案、字体排版及封面模糊状态注入 Compose 环境树 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/ZakoIcons.kt` | 246 | 自定义矢量图形资产绘制中枢 | 基于 Compose `ImageVector.Builder` 纯代码构建全屏、退出全屏、稍后再看及画幅比例图标 |

---

## 6. UI 公共复用组件 (`ui/components/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/AppPickerDialog.kt` | 262 | 外部调用应用选择器弹窗 | 展现系统及支持视频播放的应用列表，支持设为默认播放器 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/CustomColorDialog.kt` | 320 | 无级自定义主题调色盘弹窗 | 基于 HSV/RGB 颜色模型提供可视化调色滑块，支持 Hex 输入并实时预览主题配色 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderBrowserDialog.kt` | 298 | 物理文件系统目录浏览器对话框 | 遍历外部存储目录树，提供新建目录、存储权限判定与自定义下载路径选择 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderTreeDialog.kt` | 189 | 业务收藏夹多叉树层级选择弹窗 | 递归展开收藏夹目录层级，支持直接新建子文件夹并快速指定收藏归属目标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/GestureOverlays.kt` | 221 | 播放器手势调节（音量/亮度/快进）视觉反馈浮层 | 在播放视口中央弹出带图标与百分比进度的半透明胶囊/卡片 HUD 反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/PlaybackSpeedDialog.kt` | 132 | 播放倍速调节浮动弹窗 | 约束视口高度并阻断底层手势穿透，提供从 0.5x 到 3.0x 多档倍速平滑切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/PlayerBrightnessVolumeHelper.kt` | 149 | 屏幕亮度与媒体音量手势调控辅助计算器 | 结合 WindowManager 与 AudioManager，提供线性平滑归一化与防跳变调控机制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/PlayerControlDefaults.kt` | 19 | 播放器全域控制层几何尺寸与视觉补偿规范 | 标定控件图标（24dp）、触控容器（40dp）及异形图标视觉等重补偿常量 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/PlayerGestureDetector.kt` | 202 | 播放器屏幕手势统一侦测与分发器 | 监听双击、垂直滑动（左亮右音）与水平滑动（进度寻道），防误触阻断底层事件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/SpeedBadge.kt` | 55 | 播放控制条当前倍速指示徽标 | 紧凑呈现当前播放倍速标签，点击调出 `PlaybackSpeedDialog` |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoActionMenu.kt` | 124 | 全局统一视频长按上下文操作菜单组件 | 基于 `ModalBottomSheet` 消费 `MenuTokens`，接收 `List<VideoMenuAction>` 驱动选项与危险色渲染 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoCard.kt` | 152 | 瀑布流与列表通用视频卡片组件 | 展示视频封面（支持模糊脱敏）、VIP 标识、播放时长及点击事件分发 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/WavyProgressSlider.kt` | 195 | Material 3 Expressive 动态可动波浪阻尼滑块 | 结合动画过渡与正弦波振幅，实现播放进度条在拖拽/播放态下的流体波浪动效 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoBottomBar.kt` | 45 | 应用底部导航栏组件 | 封装 NavigationBar，提供首页、收藏、我的三大核心标签切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoToast.kt` | 211 | 全局防抖悬浮轻量提示组件 (Toast) | 协程通道防抖排队调度，提供带动画淡入淡出的自定义界面悬浮提示条 |

---

## 7. UI 导航与路由拓扑 (`ui/navigation/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/Screen.kt` | 41 | 页面导航路由与参数定义密封类 | 定义 Home、Detail、Player、Favorites、History、Downloads 等页面的 Route 与参数解析模板 |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/ZakoNavHost.kt` | 439 | 应用根级声明式导航图宿主 | 绑定 `NavHostController`，统筹页面跳转动效、ViewModel 注入与列表本地起播仲裁 |

---

## 8. UI 业务视窗与 ViewModel (`ui/screens/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailScreen.kt` | 1521 | 视频详情视窗，集成内嵌播放、选集、暗号提取与相关推荐 | 响应式双栏/单栏排布，承载内嵌 MPV 视口、收藏夹选择弹窗与评论交互面板 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailViewModel.kt` | 655 | 视频详情页状态中枢，负责详情拉取、收藏夹归属、稍后再看与下载创建 | 聚合 `CosApiClient`、`FavoriteRepository` 与 `DownloadManager`，暴露单一 StateFlow |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesScreen.kt` | 750 | 收藏夹树状目录管理视窗，支持多选批量操纵与长按菜单 | 结合面包屑导航展示文件夹/条目，基于 `VideoActionMenu` 实现长按重命名、移动与复制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesViewModel.kt` | 387 | 收藏夹业务状态中枢，处理层级切换、批量移动/删除与搜索 | 调度 `FavoriteRepository`，管理当前浏览目录路径、批量选中集合及搜索过滤 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeScreen.kt` | 903 | 首页聚合视窗，支持网格/瀑布流浏览、标签筛选与语法级搜索 | 集成搜索栏（暗号嗅探）、分类 Chip 组、下拉刷新与分页加载状态机隔离 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeViewModel.kt` | 460 | 首页状态机中枢，管理视频列表分页、搜索词历史与暗号直达 | 协同 `CosApiClient` 与 `SearchHistoryRepository`，流式响应排序模式与查询关键词流 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerScreen.kt` | 681 | 独立全屏播放视窗，集成控制层覆盖物、CDN 测速面板与画幅调整 | 装载 `MPVView` 纯原生渲染视口，提供顶部控制条、波浪进度条、手势 HUD 及选线抽屉 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerViewModel.kt` | 706 | 播放器状态机中枢，统筹起播选线、实时测速热切、进度保存与外部播放 | 驱动 `ZakoPlayer` 与 `CdnSmartRouter`，支持本地文件优先起播与进度自动写回历史表 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/ProfileScreen.kt` | 344 | 个人中心主入口视窗 | 提供离线下载、观看历史、稍后再看、数据备份与全局设置等子模块聚合导航列表 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/AboutScreen.kt` | 181 | 关于视窗，展示应用版本信息、开源协议与开发者鸣谢 | 包含版本信息、源码仓库跳转及彩蛋连击解锁机制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/LogViewerScreen.kt` | 284 | 实时应用日志查看器视窗 | 提供日志实时流刷新、级别筛选、关键词搜索、清空及外部导出分享功能 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreScreen.kt` | 627 | 数据备份与恢复视窗 | 提供生成全量 ZIP 备份文件、导入恢复、备份列表管理及操作进度反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreViewModel.kt` | 249 | 数据备份与恢复业务逻辑控制器 | 调度 `BackupRestoreManager` 执行流式序列化/反序列化，暴露进度与完成弹窗状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsScreen.kt` | 801 | 离线下载管理视窗，分栏管理已下载/下载中任务，支持多选与长按菜单 | 统筹自定义下载目录更换、文件信息弹窗、批量删除确认及 `VideoActionMenu` 收藏交互 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsViewModel.kt` | 239 | 离线下载业务状态中枢，负责任务流监听、暂停/继续/重下与跨目录迁移 | 监听 `DownloadManager` 进度流，处理路径迁移文件检查及调用 `FavoriteRepository` 添加收藏 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryScreen.kt` | 298 | 观看历史视窗，按天隔离分组呈现历史记录，支持批量管理与长按菜单 | 统一多选图标（`AppIcons.MultiSelect`），基于 `VideoActionMenu` 支持一键加入收藏/稍后再看 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryViewModel.kt` | 168 | 观看历史业务状态中枢，处理历史检索、批量删除及收藏夹转存 | 调度 `HistoryRepository`，流式过滤历史记录列表并管理单项/多选添加收藏状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsScreen.kt` | 150 | 全局设置主入口视窗，负责顶栏导航、分组卡片拼装与缓存清理弹窗宿主 | 仅保留页面脚手架、分类导航条目拼装及清理应用缓存确认弹窗 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsComponents.kt` | 381 | 设置同包共享基础组件与弹窗容器 | 提供 `SettingsCategoryItem`、`SettingsCard`、`SettingsSwitchItem`、`ExternalAppCard` 以及权限/迁移弹窗组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/AppearanceSettingsSection.kt` | 340 | 外观与主题设置视窗，支持莫奈动态色、自定义调色盘与图标主题跟随 | 整合莫奈取色开关、主题色预设选择列表、自定义 HEX 调色板及图标主题联动确认 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/PlaybackSettingsSection.kt` | 198 | 播放核心引擎、长按倍速与外部播放器配置视窗 | 配置长按倍速值、播放内核选择（系统/MPV/自动）、MPV 硬解模式及自定义外部播放器选择宿主 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/DataSourceSettingsSection.kt` | 115 | 数据源与在线流媒体 CDN 线路选优调度配置区块 | 提供自动测速优选节点开关及默认固定 CDN 线路（线路 1~5）筛选单选芯片组 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/StorageAndSecuritySettingsSection.kt` | 398 | 存储路径、文件权限、离线迁移、封面脱敏及长按分享配置视窗 | 整合隐私封面模糊开关、安全分享解锁开关、全盘文件权限卡片、内置目录选择与多文件迁移流程 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsViewModel.kt` | 354 | 全局配置业务状态机控制器 | 调度 `SettingsRepository`，响应式更新 DataStore 偏好并触发图标别名同步 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterScreen.kt` | 295 | 稍后再看视窗，展现待看列表并支持多选模式、批量管理与长按上下文菜单 | 统一多选图标（`AppIcons.MultiSelect`），基于 `VideoActionMenu` 支持一键加入收藏/多选联动，提供批量转存与移除操作 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterViewModel.kt` | 174 | 稍后再看业务控制器与多选状态中枢 | 监听待看流，统筹多选模式切换、全选/反选、批量移出待看及联动 `FavoriteRepository` 批量加入收藏夹 |

---

## 9. 工程构建与 CI/CD 自动化流水线

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `build.gradle.kts` | 5 | 根工程 Gradle 构建拓扑脚本 | 配置根级构建脚本与基础插件别名声明 |
| `settings.gradle.kts` | 17 | 模块依赖树与 Maven 插件仓库编排 | 定义工程名 `NeoViewer`，编排 Google、MavenCentral 及本地插件存储库 |
| `app/build.gradle.kts` | 129 | 核心应用模块构建参数、依赖声明与发版配置 | 配置 Android SDK 34、CMake 原生链接、R8 混淆、签名配置及动态版本号递增字段 |
| `.github/workflows/release.yml` | 112 | GitHub Actions 自动化发版流水线工作流 | 监听版本 Tag（`v*`），自动化构建环境，生成更新日志并自动归档发布纯源码压缩包 |
