# ZakoViewer 全局代码索引与架构映射 (Codebase Index)

> **版本定位**：截至最新构建（`v1.2.47`），全项目核心工程源码共计 **110** 个文件，累计 **24,543** 行代码。  
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
| `app/src/main/AndroidManifest.xml` | 149 | 应用全局清单，注册 Activity、前台服务、权限与文件 Provider | 声明媒体读写与存储权限、文件共享 FileProvider 及 DownloadService |
| `app/src/main/kotlin/moe/zako/viewer/ZakoApplication.kt` | 151 | 应用级 Application 单例，初始化全局日志、崩溃兜底、Coil 图像加载及核心数据仓储 | 继承 `Application` 实现 `ImageLoaderFactory`，提供数据库与仓储 lazy 门面，启动预建根收藏夹与封面迁移 |
| `app/src/main/kotlin/moe/zako/viewer/MainActivity.kt` | 99 | 单 Activity 承载宿主，处理窗口全屏边到边（Edge-to-Edge）与系统栏视觉沉浸 | 基于 `ComponentActivity`，通过 `enableEdgeToEdge()` 配置状态栏/导航栏透明度，装载 `ZakoNavHost` 根组合项 |

---

## 2. 核心架构与基建驱动 (`core/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSmartRouter.kt` | 66 | 智能 CDN 线路路由与测速结果解析仲裁 | 维护多节点元数据，结合 `CdnSpeedTester` 返回的 TTFB 和带宽动态评分并产出最佳线路 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSpeedTester.kt` | 153 | 多路 CDN 节点并发网络探测与测速引擎 | 基于 OkHttp 协程并发发起 Range 请求，探测 TCP/TLS 握手延迟与突发下行速率 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/PlaybackSourceCoordinator.kt` | 203 | 在线视频起播选线与 CDN 测速调度协调器 | 统筹自动测速选优、默认节点索引优雅回退、测速面板会话控制与流代理 URL 解析门面 |
| `app/src/main/kotlin/moe/zako/viewer/core/cover/CoverCacheManager.kt` | 437 | 视频封面本地持久化缓存、离线自愈与按需加载中枢 | 本地文件 Hash 寻址存储，封装 Coil `ImageRequest` 拦截器，支持旧版离线封面静默自愈 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadManager.kt` | 815 | 离线下载全生命周期管理中枢，协调任务调度、断点续传与存储目录迁移 | 单例维护下载任务状态机（Flow 响应式通知），透传视频时长，协调 `DownloadDao` 与本地文件物理 I/O |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadService.kt` | 58 | 离线下载前台服务，保障后台下载稳定不被系统杀进程 | 继承 Android `Service`，绑定前台通知通道并实时显示下载进度与速率通知 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/MultiNodeDownloader.kt` | 397 | 多节点并发分片断点续传下载底层实现 | 基于 OkHttp 流式拉取 HTTP Range 分片，多线程并发校验并合并写入磁盘目标文件 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/AppPickerHelper.kt` | 128 | 外部应用与播放器匹配助手 | 通过 `PackageManager` 查询匹配支持视频播放 Intent 的系统及第三方应用列表 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/ExternalLauncher.kt` | 108 | 唤起外部第三方播放器（如 VLC、MX Player）或系统相册应用 | 构造带播放 MIME 类型与授权 URI（FileProvider）的 `ACTION_VIEW` 显式/隐式 Intent |
| `app/src/main/kotlin/moe/zako/viewer/core/icon/AppIconManager.kt` | 93 | 桌面动态图标别名热切换器 | 操纵 `PackageManager.setComponentEnabledSetting` 切换 Manifest 中的 `<activity-alias>` |
| `app/src/main/kotlin/moe/zako/viewer/core/logger/ZakoLog.kt` | 521 | 结构化诊断日志框架，提供内存缓冲、磁盘滚动落盘与系统信息采集 | 单例封装双端日志输出，支持日志级别过滤、公共下载目录原子导出与崩溃诊断头组装 |
| `app/src/main/kotlin/moe/zako/viewer/core/permission/StoragePermissionHelper.kt` | 54 | 外部存储与所有文件访问权限（MANAGE_EXTERNAL_STORAGE）检查适配器 | 区分 Android 11+（`Environment.isExternalStorageManager`）与低版本运行时权限检查与设置跳转 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVLib.kt` | 157 | libmpv 原生 JNI 跨语言调用桥梁 | 声明 `create`、`init`、`destroy`、`command`、`setOptionString` 等 JNI 原生方法，精确降级硬解探测噪声 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVView.kt` | 129 | libmpv 原生视频 SurfaceView 渲染承载组件 | 继承 `SurfaceView` 实现 `SurfaceHolder.Callback`，协同原生 OpenGL/EGL 绘制表面生命周期 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MultiNodeStreamBuffer.kt` | 620 | 流媒体本地微型代理网关，提供分片缓冲与边下边播 | 基于嵌入式 Socket 建立本地代理，处理 MPV 发起的 Range 请求并并发拉取远端源 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/PlayerEngine.kt` | 124 | 内部播放引擎适配器统一接口与状态定义 | 定义 `PlayerEngineAdapter` 接口、`EngineCallback` 回调契约与状态流数据模型 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MpvPlayerEngine.kt` | 489 | libmpv 原生渲染播放引擎适配器实现 | 承载 `MPVLib` JNI 驱动、OpenGL/EGL 渲染时序、Seek 瞬态防抖与零值抑制、absolute 精确跳转及日志过滤 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/SystemPlayerEngine.kt` | 414 | 系统 MediaPlayer 播放引擎适配器实现 | 后台独立单线程安全串行派发 IPC、主线程同步解绑 Surface 杜绝 EGL 争抢、Seek 瞬态保护与线程安全释放 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/ZakoPlayer.kt` | 302 | 播放器核心对外轻量封装门面 | 适配器模式调度 MPV 与系统引擎，聚合对外 `StateFlow`，内置 `seekTo` 诊断日志，隔离跨引擎过期回调 |
| `app/src/main/kotlin/moe/zako/viewer/core/search/SearchQueryParser.kt` | 104 | 复合搜索词法解析引擎，支持高级布尔语法与暗号解析 | 词法拆解查询字串为 Tag/关键词过滤器，自动嗅探并隔离提取 SFW 汉字隐写特征码 |
| `app/src/main/kotlin/moe/zako/viewer/core/share/ShareCipherUtil.kt` | 91 | 语义级 SFW 汉字隐写编解码分享协议工具 | 建立 Base64/Hex 与自然汉字字库的高维双向映射表，实现零外链敏感拦截的暗号编解码 |
| `app/src/main/kotlin/moe/zako/viewer/core/storage/PublicStorageHelper.kt` | 107 | 分区存储公共空间直出与 MediaStore 写入适配器 | 遵循 Android 10+ Scoped Storage，通过 `MediaStore.Downloads` 写入备份包与导出日志 |

---

## 3. 原生 C/C++ 渲染管线 (`cpp/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/cpp/CMakeLists.txt` | 32 | CMake 原生编译构建配置脚本 | 声明 `mpv_jni` 共享库目标，链接预编译 `libmpv`、`libandroid`、`liblog` 与 EGL 渲染库 |
| `app/src/main/cpp/mpv_jni.cpp` | 634 | MPV JNI 桥接层与互斥锁安全同步实现 | C++17 实现 JNI 接口，采用 `std::mutex` 与条件变量保障 Surface 绑定、渲染调用与销毁线程安全 |

---

## 4. 数据持久化、仓储与网络数据源 (`data/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/data/backup/BackupRestoreManager.kt` | 590 | 跨版本数据库全量导出与导入恢复管理器 | 流式压缩/解压 ZIP 容器，校验版本元数据，原子合并/覆写 Room 表并保持屏幕常亮唤醒锁 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/ZakoDatabase.kt` | 95 | Room 主数据库入口与单例管理 | 继承 `RoomDatabase`，聚合 4 张数据表，升级至版本 4 并提供 `MIGRATION_3_4` 补齐视频时长字段 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/FavoriteDao.kt` | 118 | 树形收藏夹与收藏条目数据库数据访问对象 (DAO) | 定义 Room 查询、层级路径递归遍历、批量复制/移动及唯一根收藏夹的 SQL 事务操作 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/OtherDaos.kt` | 131 | 历史记录、稍后再看、搜索词历史与离线下载 DAO 集合 | 包含 `WatchHistoryDao`、`WatchLaterDao`（支持批量移除）、`SearchHistoryDao`、`DownloadDao` 的响应式 SQL 接口 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/FavoriteEntities.kt` | 47 | 树形收藏夹目录实体与收藏条目实体定义 | Room 实体 `FavoriteFolderEntity`（自关联 `parentId`）与 `FavoriteItemEntity` |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/LocalEntities.kt` | 84 | 历史记录、稍后再看、搜索历史与下载任务 Room 实体集合 | 定义 `WatchHistoryEntity`、`WatchLaterEntity`、`SearchHistoryEntity`、`DownloadEntity`（包含 `duration` 字段） |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/FavoriteRepository.kt` | 174 | 收藏体系业务仓库，统一维护无限级收藏夹拓扑与封面保活 | 封装 `FavoriteDao` 异步操作，提供创建/重命名/删除文件夹及条目增删与封面持久化触发 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/HistoryAndSettingsRepositories.kt` | 408 | 历史记录、稍后再看、搜索记录与全局 DataStore 配置仓储集合 | 整合 `HistoryRepository`（支持批量移出稍后再看）、`SearchHistoryRepository` 及基于 Preferences DataStore 的设置管理 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosApiClient.kt` | 207 | 视频源服务端网络请求客户端 | 封装 OkHttp 访问远端列表、检索及详情 API，支持智能线路备选与 JSON 反序列化 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosCrypto.kt` | 54 | 远端数据源响应报文加解密算法工具 | 实现视频流地址与加密元数据报文的对称解密算法 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosModels.kt` | 297 | 远端服务业务数据传输模型 (DTO) 集合 | 定义视频条目 `CosVideoItem`、详情 `CosVideoDetail`、评论、标签及分页响应模型 |

---

## 5. UI 设计令牌与主题体系 (`ui/theme/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/AppIcons.kt` | 89 | 全局统一图标 Design Token 门面 | 集中管理多选、五角星收藏、播放、删除、详情、文件及设置系统操作图标，统一五角星收藏与向右箭头 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Color.kt` | 440 | 全局色彩拓扑、调色板预设与莫奈动态取色方案 | 定义多种内置主题色盘（经典紫、自然绿、暖阳黄等）及暗色/亮色 ColorScheme 语义 token |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/FilterBarTokens.kt` | 12 | 首页筛选栏操作按钮 Design Token | 规范筛选、排序、布局切换等圆形操作按钮的尺寸（36dp）、图标大小（20dp）与元素间距（8dp） |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MenuTokens.kt` | 44 | 上下文菜单与底部操作栏样式 Design Token | 集中规范菜单容器与项圆角、标高、内边距、图标规格、文字样式及动态危险色 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/SearchBarTokens.kt` | 35 | 全局搜索框 Design Token | 集中规范搜索框胶囊圆角（28dp）、图标规格（24dp）、中性前景色约束与一键清空触感微动效时长与曲线 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Theme.kt` | 50 | Material 3 主题容器组合项与 LocalComposition 注入 | 提供 `ZakoViewerTheme`，将动态色彩方案、字体排版及封面模糊状态注入 Compose 环境树 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/ZakoIcons.kt` | 263 | 自定义矢量图形资产绘制中枢 | 基于 Compose `ImageVector.Builder` 纯代码构建全屏、退出全屏、稍后再看及画幅比例图标 |

---

## 6. UI 公共复用组件 (`ui/components/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/AppPickerDialog.kt` | 250 | 外部调用应用选择器弹窗 | 展现系统及支持视频播放的应用列表，接入 ZakoSearchBar 统一搜索视觉与交互 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/CustomColorDialog.kt` | 332 | 无级自定义主题调色盘弹窗 | 基于 HSV/RGB 颜色模型提供可视化调色滑块，支持 Hex 输入并实时预览主题配色 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/DurationBadge.kt` | 93 | 全局视频封面时长角标组件与解析器 | 兼容浮点秒、整数秒、毫秒与已格式化字符串，于封面右下角呈现统一半透明圆角角标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FilterBarCircleButton.kt` | 48 | 筛选栏统一圆形操作图标按钮 | 统筹筛选、排序与布局切换按钮尺寸、Tonal 默认着色（secondaryContainer/onSecondaryContainer）与高对比激活态（primary/onPrimary） |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderBrowserDialog.kt` | 360 | 物理文件系统目录浏览器对话框 | 遍历外部存储目录树，提供新建目录、存储权限判定、ON_RESUME 权限恢复监听与下载路径选择 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderTreeDialog.kt` | 198 | 业务收藏夹多叉树层级选择弹窗 | 递归展开收藏夹目录层级，支持直接新建子文件夹并快速指定收藏归属目标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/LocalFileInfoDialog.kt` | 155 | 全局统一本地视频文件详细信息弹窗 | 跨 4 列表页复用，呈现视频标题、文件全路径、磁盘大小、分辨率及时长信息 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/MultiSelectTopBar.kt` | 59 | 全局统一多选态顶栏组件 | 封装退出多选导航图标、已选数量计数、全选/反选操作、动态返回键拦截与 actions 扩展插槽 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoActionMenu.kt` | 131 | 全局统一视频长按上下文操作菜单组件 | 基于 `ModalBottomSheet` 消费 `MenuTokens`，接收 `List<VideoMenuAction>` 驱动选项与危险色渲染 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoCard.kt` | 148 | 瀑布流与列表通用视频卡片组件 | 展示视频封面（支持模糊脱敏）、VIP 标识、复用 `DurationBadge` 呈现时长及点击事件分发 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/WavyProgressSlider.kt` | 194 | Material 3 Expressive 动态可动波浪阻尼滑块 | 结合动画过渡与正弦波振幅，rememberUpdatedState 解决闭包过期并精准下发落点比例 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoBottomBar.kt` | 49 | 应用底部导航栏组件 | 封装 NavigationBar，提供首页、收藏（五角星 Token）、我的三大核心标签切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoSearchBar.kt` | 147 | 全应用统一胶囊搜索框与带微动效清空组件 | 规范胶囊圆角（28dp）、中性前景色（onSurfaceVariant），集成 fadeIn/scaleIn 显隐过渡及点击回弹旋转触感微动效 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoToast.kt` | 222 | 全局防抖悬浮轻量提示组件 (Toast) | 协程通道防抖排队调度，提供带动画淡入淡出的自定义界面悬浮提示条 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/CdnNodeSelectorDialog.kt` | 97 | CDN 节点实时测速与线路切换抽屉 | 消费 `MenuTokens` 与 `AppIcons`，跨全屏与详情页内嵌播放器复用测速/手动选线 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/GestureOverlays.kt` | 228 | 播放器手势调节（音量/亮度/快进）视觉反馈浮层 | 在播放视口中央弹出带图标与百分比进度的半透明胶囊/卡片 HUD 反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlaybackSpeedDialog.kt` | 140 | 播放倍速调节浮动弹窗 | 约束视口高度并阻断底层手势穿透，提供从 0.5x 到 3.0x 多档倍速平滑切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBottomControls.kt` | 147 | 全局共享播放底部控制栏与波浪进度条 | 统筹播放/暂停、时间进度、波浪滑块、全屏切换，集成寻道目标保持杜绝瞬态闪回旧进度 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBrightnessVolumeHelper.kt` | 161 | 屏幕亮度与媒体音量手势调控辅助计算器 | 结合 WindowManager 与 AudioManager，提供线性平滑归一化与防跳变调控机制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerControlDefaults.kt` | 26 | 播放器全域控制层几何尺寸与视觉补偿规范 | 标定控件图标（24dp）、触控容器（40dp）及异形图标视觉等重补偿常量 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerEngineAndHwDecDialogs.kt` | 114 | 播放内核与硬件解码切换弹窗集 | 提供 ExoPlayer/MPV 播放引擎切换与 MPV 硬件解码模式选择，跨播放场景复用 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerGestureDetector.kt` | 223 | 播放器屏幕手势统一侦测与分发器 | 监听双击、垂直滑动（左亮右音）与水平滑动（进度寻道），防误触阻断底层事件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerTopBar.kt` | 147 | 全局共享播放器顶栏组件 | 统筹返回导航、视频标题、画幅/内核/解码设置按钮与插槽扩展 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/SpeedBadge.kt` | 58 | 播放控制条当前倍速指示徽标 | 紧凑呈现当前播放倍速标签，点击调出 `PlaybackSpeedDialog` |

---

## 7. UI 导航与路由拓扑 (`ui/navigation/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/Screen.kt` | 47 | 页面导航路由与参数定义密封类 | 定义 Home、Detail、Player、Favorites、History、Downloads 等页面的 Route 与参数解析模板，统一历史记录与五角星收藏 |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/ZakoNavHost.kt` | 440 | 应用根级声明式导航图宿主 | 绑定 `NavHostController`，统筹页面跳转动效与 ViewModel 注入，详情页跳转播放器契约透传 forceOnline 强制在线 |

---

## 8. UI 业务视窗与 ViewModel (`ui/screens/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailScreen.kt` | 280 | 视频详情骨架视窗，承载响应式布局与弹窗宿主 | 响应式双栏/单栏排布，管理原地横屏全屏沉浸（锁定强制在线片源）、返回拦截、收藏夹树选择与下载弹窗宿主 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInlinePlayerSection.kt` | 329 | 视频详情内嵌播放器视口容器与控制层 | 维持 AndroidView 组合树稳定，复用 PlayerTopBar 与 PlayerBottomControls，落实全屏原地展开维持在线源契约 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInfoAndActionsSection.kt` | 411 | 视频标题、核心操作栏与元数据展示卡片 | 呈现 VIP 徽标、可复制标题，集成五角星收藏/下载/稍后再看/暗号分享操作栏及点击检索元数据芯片 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailRelatedAndCommentsSection.kt` | 272 | 相关推荐视频列表与留言区交互面板 | 折叠/展开留言评论区、横屏网格与竖屏列表相关视频渲染，复用共享弹窗组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailViewModel.kt` | 649 | 视频详情页状态中枢，负责详情拉取、收藏夹归属、稍后再看与下载创建 | 聚合各仓库门面，setFullscreen 契约锁定全屏维持当前在线片源与进度，统筹选线起播与测速会话 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesScreen.kt` | 334 | 收藏夹树状目录管理视窗，支持多选批量操纵与长按菜单 | 统一多选反选逻辑，长按固定跳转详情页、本地视频弹出 `LocalFileInfoDialog`，接入 `MultiSelectTopBar` 与 `ZakoSearchBar` |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoriteTreeItems.kt` | 300 | 收藏夹树状组件（面包屑路径条、子收藏夹卡片与视频卡片） | 呈现 `BreadcrumbBar` 路径芯片、`FavoriteFolderItem` 文件夹卡片与接入 `DurationBadge` 的视频卡片 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesDialogs.kt` | 272 | 收藏夹业务弹窗与操作抽屉宿主 | 提供新建/重命名文件夹弹窗、重命名视频弹窗及统一步调的长按操作抽屉 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesViewModel.kt` | 415 | 收藏夹业务状态中枢，处理层级切换、批量移动/删除与搜索 | 调度 `FavoriteRepository`，原子清空 `toggleSelectAll()`，统一 `LocalFileInfo` 映射 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeScreen.kt` | 410 | 首页主页面视窗，负责列表滚动骨架、瀑布流/网格排布与状态机调度 | 维护下拉刷新、触底分页预加载、`!isLoading && error == null && items.isEmpty()` 明确状态机隔离，调度搜索与筛选条 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeSearchBar.kt` | 202 | 首页搜索输入框、暗号直达提示与搜索历史交互面板 | 彻底移除向右箭头，复用 `ZakoSearchBar` 统一胶囊搜索框与中性色一键清空微动效，统筹搜索历史展开与批量删除 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeFilterBar.kt` | 405 | 首页分类标签栏、排序模式切换、视图切换与综合筛选抽屉 | 复用 `FilterBarCircleButton` 统一筛选、排序、布局切换按钮着色（M3 Tonal 默认态 + 高对比激活态），消除视觉割裂与收敛间距 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeViewModel.kt` | 502 | 首页状态机中枢，管理视频列表分页、搜索词历史与暗号直达 | 协同 `CosApiClient` 与 `SearchHistoryRepository`，流式响应排序模式与查询关键词流 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerScreen.kt` | 370 | 独立全屏播放视窗，集成控制层覆盖物、CDN 测速面板与画幅调整 | 装载 `MPVView` 纯原生渲染视口，全面复用 `PlayerTopBar`、`PlayerBottomControls` 及共享弹窗组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerViewModel.kt` | 641 | 播放器状态机中枢，统筹起播选线、实时测速热切、进度保存与外部播放 | 驱动 `ZakoPlayer` 与 `PlaybackSourceCoordinator`，统筹本地视频优先起播、在线路由起播与测速面板切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/ProfileScreen.kt` | 365 | 个人中心主入口视窗 | 全局更名历史记录，统一五角星收藏图标，提供离线下载、稍后再看、数据备份与全局设置等子模块聚合导航列表 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/AboutScreen.kt` | 191 | 关于视窗，展示应用版本信息、开源协议与开发者鸣谢 | 包含版本信息、源码仓库跳转及彩蛋连击解锁机制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/LogViewerScreen.kt` | 291 | 实时应用日志查看器视窗 | 提供日志实时流刷新、级别筛选、关键词搜索、清空及外部导出分享功能 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreScreen.kt` | 667 | 数据备份与恢复视窗 | 统一历史记录备份文案，提供生成全量 ZIP 备份文件、导入恢复、备份列表管理及操作进度反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreViewModel.kt` | 265 | 数据备份与恢复业务逻辑控制器 | 调度 `BackupRestoreManager` 执行流式序列化/反序列化，暴露进度与完成弹窗状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsScreen.kt` | 364 | 离线下载管理视窗，分栏管理已下载/下载中任务，支持多选与长按菜单 | 接入 `LocalFileInfoDialog`，长按固定查看详情，修复 `toggleSelectAll()` 原子清空，ON_RESUME 权限恢复监听 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadTaskItems.kt` | 293 | 离线下载任务条目卡片组件（已下载卡片与下载中卡片） | 接入 `DurationBadge` 呈现封面时长角标，提供已下载与下载中任务进度/交互 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsDialogs.kt` | 254 | 离线下载弹窗与长按抽屉宿主 | 提供 `DownloadActionBottomSheet` 菜单，统一文案「查看详情」，移除冗余文件信息弹窗复用公共组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsViewModel.kt` | 264 | 离线下载业务状态中枢，负责任务流监听、暂停/继续/重下与跨目录迁移 | 监听 `DownloadManager` 进度流，修复 `toggleSelectAll()` 判定与处理路径迁移 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryScreen.kt` | 344 | 历史记录视窗，按天隔离分组呈现历史记录，支持批量管理与长按菜单 | 接入 `DurationBadge` 时长角标与 `ZakoSearchBar` 全局统一搜索框，长按固定跳转详情页、本地视频弹出 `LocalFileInfoDialog` |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryViewModel.kt` | 198 | 历史记录业务状态中枢，处理历史检索、批量删除及收藏夹转存 | 调度 `HistoryRepository`，提供 `toggleSelectAll()` 原子清空与多选添加收藏状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsScreen.kt` | 150 | 全局设置主入口视窗，负责顶栏导航、分组卡片拼装与缓存清理弹窗宿主 | 仅保留页面脚手架、分类导航条目拼装及清理应用缓存确认弹窗 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsComponents.kt` | 381 | 设置同包共享基础组件与弹窗容器 | 提供 `SettingsCategoryItem`、`SettingsCard`、`SettingsSwitchItem`、`ExternalAppCard` 以及权限/迁移弹窗组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/AppearanceSettingsSection.kt` | 340 | 外观与主题设置视窗，支持莫奈动态色、自定义调色盘与图标主题跟随 | 整合莫奈取色开关、主题色预设选择列表、自定义 HEX 调色板及图标主题联动确认 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/PlaybackSettingsSection.kt` | 198 | 播放核心引擎、长按倍速与外部播放器配置视窗 | 配置长按倍速值、播放内核选择（系统/MPV/自动）、MPV 硬解模式及自定义外部播放器选择宿主 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/DataSourceSettingsSection.kt` | 115 | 数据源与在线流媒体 CDN 线路选优调度配置区块 | 提供自动测速优选节点开关及默认固定 CDN 线路（线路 1~5）筛选单选芯片组 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/StorageAndSecuritySettingsSection.kt` | 401 | 存储路径、文件权限、离线迁移、封面脱敏及长按分享配置视窗 | 整合隐私封面模糊开关、安全分享解锁开关、全盘文件权限卡片（ON_RESUME 自动刷新）、内置目录选择与多文件迁移流程 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsViewModel.kt` | 354 | 全局配置业务状态机控制器 | 调度 `SettingsRepository`，响应式更新 DataStore 偏好并触发图标别名同步 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterScreen.kt` | 337 | 稍后再看视窗，展现待看列表并支持多选模式、批量管理与长按上下文菜单 | 接入 `DurationBadge`，统一长按「查看详情」、检测本地视频展示「文件信息」，接入 `MultiSelectTopBar` 统一全选/反选 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterViewModel.kt` | 193 | 稍后再看业务控制器与多选状态中枢 | 监听待看流，统筹多选模式切换、`toggleSelectAll()` 全选/反选、批量移出待看及联动 `FavoriteRepository` 批量加入收藏夹 |

---

## 9. 工程构建与 CI/CD 自动化流水线

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `build.gradle.kts` | 5 | 根工程 Gradle 构建拓扑脚本 | 配置根级构建脚本与基础插件别名声明 |
| `settings.gradle.kts` | 18 | 模块依赖树与 Maven 插件仓库编排 | 定义工程名 `NeoViewer`，编排 Google、MavenCentral 及本地插件存储库 |
| `app/build.gradle.kts` | 129 | 核心应用模块构建参数、依赖声明与发版配置 | 配置 Android SDK 34、CMake 原生链接、R8 混淆、签名配置及动态版本号递增字段（v1.2.47 / versionCode 51） |
| `.github/workflows/release.yml` | 130 | GitHub Actions 自动化发版流水线工作流 | 监听版本 Tag（`v*`），自动化构建环境，生成更新日志并自动归档发布纯源码压缩包 |
