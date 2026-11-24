# ZakoViewer 全局代码索引与架构映射 (Codebase Index)

> **版本定位**：截至最新构建（v1.3.15），全项目核心工程源码共计 **126** 个文件，累计 **30,613** 行代码。  
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
| `app/src/main/kotlin/moe/zako/viewer/ZakoApplication.kt` | 140 | 应用级 Application 单例，初始化全局日志、崩溃兜底、Coil 图像加载及核心数据仓储 | 继承 `Application` 实现 `ImageLoaderFactory`，提供数据库与仓储 lazy 门面，启动预建根收藏夹与封面迁移 |
| `app/src/main/kotlin/moe/zako/viewer/MainActivity.kt` | 99 | 单 Activity 承载宿主，处理窗口全屏边到边（Edge-to-Edge）与系统栏视觉沉浸 | 基于 `ComponentActivity`，通过 `enableEdgeToEdge()` 配置状态栏/导航栏透明度，装载 `ZakoNavHost` 根组合项 |

---

## 2. 核心架构与基建驱动 (`core/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSmartRouter.kt` | 79 | 智能 CDN 线路路由与测速结果解析仲裁 | 维护多节点元数据，结合 `CdnSpeedTester` 返回的 TTFB 和带宽动态评分并产出最佳线路 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/CdnSpeedTester.kt` | 174 | 多路 CDN 节点并发网络探测与测速引擎 | 基于 OkHttp 协程并发发起 Range 请求，探测 TCP/TLS 握手延迟与突发下行速率 |
| `app/src/main/kotlin/moe/zako/viewer/core/cdn/PlaybackSourceCoordinator.kt` | 225 | 在线视频起播选线与 CDN 测速调度协调器 | 统筹自动测速选优、默认节点索引优雅回退、测速面板会话控制与流代理 URL 解析门面 |
| `app/src/main/kotlin/moe/zako/viewer/core/cover/CoverCacheManager.kt` | 428 | 视频封面本地持久化缓存、离线自愈与按需加载中枢 | 本地文件 Hash 寻址存储，封装 Coil `ImageRequest` 拦截器，支持旧版离线封面静默自愈 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadManager.kt` | 543 | 离线下载全生命周期管理中枢门面，协调任务调度、断点续传与前台通知 | 单例维护下载任务状态机（Flow 响应式通知），暴露 completedVideoIdsFlow 响应式已完成视频 ID 集合，透传视频时长，统筹本地完成态视频检测，委托 DownloadMigrationHelper 执行存储目录平滑迁移 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadMigrationHelper.kt` | 232 | 离线下载物理存储目录迁移与历史版本平滑升级助手 | 承接跨目录批量文件迁移、StorageConstants 64KB 流式安全缓冲复制、数据库路径原子更新与旧版 NeoViewer 目录平滑兼容 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/DownloadService.kt` | 58 | 离线下载前台服务，保障后台下载稳定不被系统杀进程 | 继承 Android `Service`，绑定前台通知通道并实时显示下载进度与速率通知 |
| `app/src/main/kotlin/moe/zako/viewer/core/duration/VideoDurationManager.kt` | 207 | 多模块视频时长静默自愈与自动补齐中枢管理器 | 基于三级优先级（跨表查询 -> 本地视频解析 -> 远端接口回填）执行限流去重后台补齐并同步刷新 Room 数据库 |
| `app/src/main/kotlin/moe/zako/viewer/core/download/MultiNodeDownloader.kt` | 402 | 多节点并发分片断点续传下载底层实现 | 基于 OkHttp 流式拉取 HTTP Range 分片，多线程并发校验并合并写入磁盘目标文件 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/AppPickerHelper.kt` | 128 | 外部应用与播放器匹配助手 | 通过 `PackageManager` 查询匹配支持视频播放 Intent 的系统及第三方应用列表 |
| `app/src/main/kotlin/moe/zako/viewer/core/external/ExternalLauncher.kt` | 108 | 唤起外部第三方播放器（如 VLC、MX Player）或系统相册应用 | 构造带播放 MIME 类型与授权 URI（FileProvider）的 `ACTION_VIEW` 显式/隐式 Intent |
| `app/src/main/kotlin/moe/zako/viewer/core/icon/AppIconManager.kt` | 93 | 桌面动态图标别名热切换器 | 操纵 `PackageManager.setComponentEnabledSetting` 切换 Manifest 中的 `<activity-alias>` |
| `app/src/main/kotlin/moe/zako/viewer/core/logger/ZakoLog.kt` | 513 | 结构化诊断日志框架，提供内存缓冲、磁盘滚动落盘与系统信息采集 | 单例封装双端日志输出，支持日志级别过滤、公共下载目录原子导出与崩溃诊断头组装 |
| `app/src/main/kotlin/moe/zako/viewer/core/network/NetworkClientProvider.kt` | 103 | 全局共享网络客户端与 OkHttpClient 派生架构中枢 | 单例维护底层共享 ConnectionPool 与专用派生客户端（API/下载/封面/测速/流媒体/Coil），提供带 Contracts 的 isHttpUrl 扩展 |
| `app/src/main/kotlin/moe/zako/viewer/core/permission/StoragePermissionHelper.kt` | 54 | 外部存储与所有文件访问权限（MANAGE_EXTERNAL_STORAGE）检查适配器 | 区分 Android 11+（`Environment.isExternalStorageManager`）与低版本运行时权限检查与设置跳转 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVLib.kt` | 179 | libmpv 原生 JNI 跨语言调用桥梁 | 声明 `create`、`init`、`destroy`、`command`、`setOptionString` 等 JNI 原生方法，精确降级硬解探测噪声 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MPVView.kt` | 146 | libmpv 原生视频 SurfaceView 渲染承载组件 | 继承 `SurfaceView` 实现 `SurfaceHolder.Callback`，协同原生 OpenGL/EGL 绘制表面生命周期 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MultiNodeStreamBuffer.kt` | 702 | 流媒体本地微型代理网关，提供分片缓冲与边下边播 | 基于嵌入式 Socket 建立本地代理，处理 MPV 发起的 Range 请求并并发拉取远端源 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/PlayerEngine.kt` | 124 | 内部播放引擎适配器统一接口与状态定义 | 定义 `PlayerEngineAdapter` 接口、`EngineCallback` 回调契约与状态流数据模型 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/MpvPlayerEngine.kt` | 545 | libmpv 原生渲染播放引擎适配器实现 | 承载 `MPVLib` JNI 驱动、OpenGL/EGL 渲染时序、Seek 瞬态防抖与零值抑制、absolute 精确跳转及日志过滤 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/SystemPlayerEngine.kt` | 422 | 系统 MediaPlayer 播放引擎适配器实现 | 后台独立单线程安全串行派发 IPC、主线程同步解绑 Surface 杜绝 EGL 争抢、Seek 瞬态保护与线程安全释放 |
| `app/src/main/kotlin/moe/zako/viewer/core/player/ZakoPlayer.kt` | 355 | 播放器核心对外轻量封装门面 | 适配器模式调度 MPV 与系统引擎，聚合对外 `StateFlow`，内置 `seekTo` 诊断日志，隔离跨引擎过期回调 |
| `app/src/main/kotlin/moe/zako/viewer/core/search/SearchQueryParser.kt` | 122 | 复合搜索词法解析引擎，支持高级布尔语法与暗号解析 | 词法拆解查询字串为 Tag/关键词过滤器，支持版本号 Base64 数据源解锁暗号匹配，隔离提取隐写特征码 |
| `app/src/main/kotlin/moe/zako/viewer/core/share/ShareCipherUtil.kt` | 89 | 语义级 SFW 汉字隐写编解码分享协议工具 | 建立 Base64/Hex 与自然汉字字库的高维双向映射表，实现零外链敏感拦截的暗号编解码 |
| `app/src/main/kotlin/moe/zako/viewer/core/storage/PublicStorageHelper.kt` | 101 | 分区存储公共空间直出与 MediaStore 写入适配器 | 遵循 Android 10+ Scoped Storage，通过 `MediaStore.Downloads` 写入备份包与导出日志，常量收敛对齐 StorageConstants |
| `app/src/main/kotlin/moe/zako/viewer/core/storage/StorageConstants.kt` | 43 | 全局存储路径、I/O 缓冲区与大小格式化底层底座 | 集中定义公共目录（ZakoViewer）、64KB/32KB 流式处理缓冲区与权威 formatBytes 文件大小格式化入口 |

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
| `app/src/main/kotlin/moe/zako/viewer/data/backup/BackupRestoreManager.kt` | 580 | 跨版本数据库全量导出与导入恢复管理器 | 流式压缩/解压 ZIP 容器，校验版本元数据，流式缓冲区收敛至 StorageConstants，原子合并/覆写 Room 表并保持屏幕常亮唤醒锁，恢复下载记录补齐时长字段 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/ZakoDatabase.kt` | 95 | Room 主数据库入口与单例管理 | 继承 `RoomDatabase`，聚合 4 张数据表，升级至版本 4 并提供 `MIGRATION_3_4` 补齐视频时长字段 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/FavoriteDao.kt` | 127 | 树形收藏夹与收藏条目数据库数据访问对象 (DAO) | 定义 Room 查询、层级路径递归遍历、批量复制/移动及唯一根收藏夹的 SQL 事务操作 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/dao/OtherDaos.kt` | 143 | 历史记录、稍后再看、搜索词历史与离线下载 DAO 集合 | 包含 `WatchHistoryDao`、`WatchLaterDao`（支持批量移除）、`SearchHistoryDao`、`DownloadDao` 的响应式 SQL 接口 |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/FavoriteEntities.kt` | 47 | 树形收藏夹目录实体与收藏条目实体定义 | Room 实体 `FavoriteFolderEntity`（自关联 `parentId`）与 `FavoriteItemEntity` |
| `app/src/main/kotlin/moe/zako/viewer/data/local/entities/LocalEntities.kt` | 84 | 历史记录、稍后再看、搜索历史与下载任务 Room 实体集合 | 定义 `WatchHistoryEntity`、`WatchLaterEntity`、`SearchHistoryEntity`、`DownloadEntity`（包含 `duration` 字段） |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/FavoriteRepository.kt` | 183 | 收藏体系业务仓库，统一维护无限级收藏夹拓扑与封面保活 | 封装 `FavoriteDao` 异步操作，提供创建/重命名/删除文件夹及条目增删与封面持久化触发 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/HistoryRepository.kt` | 172 | 观看历史、稍后再看与搜索记录业务仓储 | 统筹观看记录、稍后再看队列响应式数据流、多选批量转存收藏夹及搜索词历史（限制 50 条），recordWatch 与 addToWatchLater 时长自愈兜底合并 |
| `app/src/main/kotlin/moe/zako/viewer/data/repository/SettingsRepository.kt` | 375 | 全局用户偏好与设置仓储 | 基于 Preferences DataStore 响应式管理播放器引擎、莫奈配色、数据源插件开关与全局偏好，防覆盖持久化 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosApiClient.kt` | 258 | 视频源服务端网络请求客户端 | 封装可取消 OkHttp 请求（`suspendCancellableCoroutine` 与底层 Socket 立即熔断）访问远端列表、检索及详情 API，全链路约束 1-based 页码，支持业务状态码与限流响应（`CosApiException`）精确解析、智能线路备选与 JSON 反序列化 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosCrypto.kt` | 53 | 远端数据源响应报文加解密算法工具 | 实现视频流地址与加密元数据报文的对称解密算法 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosModels.kt` | 309 | 远端服务业务数据传输模型 (DTO) 集合 | 定义视频条目 `CosVideoItem`、详情 `CosVideoDetail`、业务异常 `CosApiException`、评论、标签及包含状态码与错误消息的分页响应模型 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/VideoDataSource.kt` | 27 | 视频数据源标准抽象插件接口 | 定义数据源 ID、显示名称、推荐流、列表搜索及视频详情标准契约方法 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/DataSourceRegistry.kt` | 43 | 全局数据源插件注册中心单例门面 | 统一管理内置/外部数据源插件注册，暴露响应式激活数据源 StateFlow 与当前可用源快照 |
| `app/src/main/kotlin/moe/zako/viewer/data/source/cosav/CosAvDataSource.kt` | 31 | CosAV 内置视频数据源插件实现 | 实现 VideoDataSource 标准接口，委托底层 CosApiClient 实施网络请求与数据转换 |

---

## 5. UI 设计令牌与主题体系 (`ui/theme/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/AppIcons.kt` | 195 | 全局统一图标 Design Token 门面 | 集中统一管理应用内的矢量图标引用，收敛 Refresh/License/MultiSelect 语义图标，全仓业务与组件代码直接引用 Icons/ZakoIcons 归零 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Color.kt` | 496 | 全局色彩拓扑、调色板预设与莫奈动态取色方案 | 定义多种内置主题色盘、PlayerOverlayTokens 及自适应深浅色 LogLevelColors 诊断日志颜色，全仓裸写 Color(0x...) 归零 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Dimens.kt` | 317 | 全局基础尺寸、双层间距、圆角、图标大小与标高设计令牌 | 集中规范语义化双层间距 SectionSpacing 与 GroupItemSpacing、圆角 CornerRadius、图标尺寸 IconSize、SettingsTokens 与 ProfileTokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/FilterBarTokens.kt` | 14 | 首页筛选栏操作按钮 Design Token | 集中规范首页筛选、排序、布局切换等圆形操作按钮的尺寸、图标及间距，底层对齐 Dimens 基础令牌 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MenuTokens.kt` | 46 | 上下文菜单与底部操作栏样式 Design Token | 集中规范长按菜单容器与项圆角、标高、内边距、图标规格、文字样式及动态危险色，底层对齐 Dimens 基础令牌 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/MotionTokens.kt` | 92 | 全局微动效时长、缓动曲线与过渡规格设计令牌 | 严格对齐 Material 3 Expressive 规范按压、淡入淡出、旋转回弹时长与缓动曲线 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/SearchBarTokens.kt` | 40 | 全局搜索框 Design Token | 集中规范搜索框胶囊圆角、图标规格、中性色约束与触感微动效参数，底层对齐 Dimens 与 MotionTokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Theme.kt` | 52 | Material 3 主题容器组合项与 LocalComposition 注入 | 提供 `ZakoViewerTheme`，将动态色彩方案、ZakoTypography 排版及封面模糊状态注入 Compose 环境树 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/Typography.kt` | 176 | 全局排版文字与业务专用样式设计令牌 | 统筹 Material 3 全层级文字排版规格、视频时长角标及日志等宽样式 |
| `app/src/main/kotlin/moe/zako/viewer/ui/theme/ZakoIcons.kt` | 263 | 自定义矢量图形资产绘制中枢 | 基于 Compose `ImageVector.Builder` 纯代码构建全屏、退出全屏、稍后再看及画幅比例图标 |

---

## 6. UI 公共复用组件 (`ui/components/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/AppPickerDialog.kt` | 296 | 外部调用应用选择器弹窗 | 展现系统及支持视频播放的应用列表，顺手收敛 AppIcons.Apps 并接入滑动与点击外部失焦修饰符 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/CustomColorDialog.kt` | 356 | 无级自定义主题调色盘弹窗 | 基于 HSV/RGB 颜色模型提供可视化调色滑块，支持 Hex 输入并实时预览主题配色 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/DurationBadge.kt` | 79 | 全局视频封面时长角标组件与解析器 | 兼容浮点秒、整数秒、毫秒与已格式化字符串，于封面右下角呈现统一半透明圆角角标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FilterBarCircleButton.kt` | 51 | 筛选栏统一圆形操作图标按钮 | 统筹筛选、排序与布局切换按钮尺寸、Tonal 默认着色（secondaryContainer/onSecondaryContainer）与高对比激活态（primary/onPrimary） |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderBrowserDialog.kt` | 371 | 物理文件系统目录浏览器对话框 | 遍历外部存储目录树，提供新建目录、存储权限判定、ON_RESUME 权限恢复监听与下载路径选择 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/FolderTreeDialog.kt` | 222 | 业务收藏夹多叉树层级选择弹窗 | 递归展开收藏夹目录层级，支持直接新建子文件夹并快速指定收藏归属目标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/LocalFileInfoDialog.kt` | 160 | 全局统一本地视频文件详细信息弹窗 | 跨 4 列表页复用，呈现视频标题、文件全路径、磁盘大小、分辨率及时长信息 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/MigrationProgressDialog.kt` | 103 | 跨目录迁移确认与进度统一对话框 | 整合离线下载管理与全局存储设置两处的目录迁移确认及进度展示，严格收敛尺寸与间距到 Design Tokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/MultiSelectTopBar.kt` | 89 | 全局统一多选态顶栏组件 | 封装退出多选导航图标、已选数量计数、全选/反选操作、动态返回键拦截，右上角统一收敛为竖排三点图标按钮（当已选 0 项时置灰禁用）并提供 menuContent 批量菜单插槽与 actions 扩展 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ObserveStoragePermissionOnResume.kt` | 50 | 统一存储权限生命周期恢复监听组件 | 跨页面复用 LifecycleEventObserver，在 ON_RESUME 状态自动重检并同步存储权限刷新 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoActionMenu.kt` | 339 | 全局统一视频长按抽屉与多选顶栏下拉菜单基座组件（Zygote 模式） | 对外暴露 `StandardVideoActionMenu`（长按抽屉）与 `StandardVideoActionDropdown`（多选顶栏下拉菜单），严格共用 `BaseVideoMenuAction` 枚举与内部统一三段式构建逻辑，三段式结构支持 `excluded` 做减法、专属插槽与底部危险操作，底层裸列表渲染函数设为 `private` 封口杜绝绕过 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoCard.kt` | 162 | 瀑布流与列表通用视频卡片组件 | 展示视频封面（支持模糊脱敏）、VIP 标识、复用 `DurationBadge` 呈现时长及点击事件分发 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/VideoListItemCard.kt` | 188 | 全局统一横向视频列表卡片组件 | 统筹封面、时长角标、多选复选框、本地已缓存标识、标题与操作菜单，全面复用至收藏、下载、历史及稍后再看列表 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/WavyProgressSlider.kt` | 233 | Material 3 Expressive 动态可动波浪阻尼滑块 | 结合动画过渡与正弦波振幅，rememberUpdatedState 解决闭包过期并精准下发落点比例 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoBottomBar.kt` | 52 | 应用底部导航栏组件 | 封装 NavigationBar，提供首页、收藏（五角星 Token）、我的三大核心标签切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoSearchBar.kt` | 256 | 全应用统一胶囊搜索框与自动失焦公共修饰符 | 规范胶囊圆角与中性色，集成软键盘展开收起监听跳变判定、搜索提交自动失焦、可复用滑动（clearFocusOnScroll）与点击外部失焦（clearFocusOnTap）修饰符 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/ZakoToast.kt` | 224 | 全局防抖悬浮轻量提示组件 (Toast) | 协程通道防抖排队调度，提供带动画淡入淡出的自定义界面悬浮提示条 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/CdnNodeSelectorDialog.kt` | 110 | CDN 节点实时测速与线路切换抽屉 | 消费 `MenuTokens` 与 `AppIcons`，跨全屏与详情页内嵌播放器复用测速/手动选线 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/GestureOverlays.kt` | 241 | 播放器手势调节（音量/亮度/快进）视觉反馈浮层 | 在播放视口中央弹出带图标与百分比进度的半透明胶囊/卡片 HUD 反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlaybackSpeedDialog.kt` | 154 | 播放倍速调节浮动弹窗 | 约束视口高度并阻断底层手势穿透，提供从 0.5x 到 3.0x 多档倍速平滑切换 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBottomControls.kt` | 173 | 全局共享播放底部控制栏与波浪进度条 | 统筹播放/暂停、时间进度、波浪滑块、全屏切换，集成寻道目标保持杜绝瞬态闪回旧进度 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerBrightnessVolumeHelper.kt` | 161 | 屏幕亮度与媒体音量手势调控辅助计算器 | 结合 WindowManager 与 AudioManager，提供线性平滑归一化与防跳变调控机制 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerControlDefaults.kt` | 40 | 播放器全域控制层几何尺寸与视觉补偿规范 | 标定控件图标（IconSize.Lg）、触控容器（TouchTarget.Compact）及异形图标视觉等重补偿常量，对齐 Dimens 基础令牌 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerEngineAndHwDecDialogs.kt` | 124 | 播放内核与硬件解码切换弹窗集 | 提供 ExoPlayer/MPV 播放引擎切换与 MPV 硬件解码模式选择，跨播放场景复用 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerGestureDetector.kt` | 249 | 播放器屏幕手势统一侦测与分发器 | 监听双击、垂直滑动（左亮右音）与水平滑动（进度寻道），防误触阻断底层事件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/PlayerTopBar.kt` | 154 | 全局共享播放器顶栏组件 | 统筹返回导航、视频标题、画幅/内核/解码设置按钮与插槽扩展 |
| `app/src/main/kotlin/moe/zako/viewer/ui/components/player/SpeedBadge.kt` | 66 | 播放控制条当前倍速指示徽标 | 紧凑呈现当前播放倍速标签，点击调出 `PlaybackSpeedDialog` |

---

## 7. UI 导航与路由拓扑 (`ui/navigation/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/Screen.kt` | 45 | 页面导航路由与参数定义密封类 | 定义 Home、Detail、Player、Favorites、History、Downloads 等页面的 Route 与参数解析模板，统一历史记录与五角星收藏 |
| `app/src/main/kotlin/moe/zako/viewer/ui/navigation/ZakoNavHost.kt` | 502 | 应用根级声明式导航图宿主 | 绑定 `NavHostController`，统筹页面跳转动效与 ViewModel 注入，显式注入 downloadManager 门面与 historyRepository 稍后再看链路，透传 forceOnline 契约 |

---

## 8. UI 业务视窗与 ViewModel (`ui/screens/`)

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailScreen.kt` | 325 | 视频详情骨架视窗，承载响应式布局与弹窗宿主 | 响应式双栏/单栏排布，管理原地横屏全屏沉浸（锁定强制在线片源）、返回拦截、收藏夹树选择与下载弹窗宿主 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInlinePlayerSection.kt` | 411 | 视频详情内嵌播放器视口容器与控制层 | 维持 AndroidView 组合树稳定，复用 PlayerTopBar 与 PlayerBottomControls，对齐「更多」菜单 4 字规范，维持在线源契约 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailInfoAndActionsSection.kt` | 429 | 视频标题、核心操作栏与元数据展示卡片 | 呈现 VIP 徽标、可复制标题，集成五角星收藏/下载/稍后再看/暗号分享操作栏及点击检索元数据芯片 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/DetailRelatedAndCommentsSection.kt` | 284 | 相关推荐视频列表与留言区交互面板 | 折叠/展开留言评论区、横屏网格与竖屏列表相关视频渲染，复用共享弹窗组件 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/detail/VideoDetailViewModel.kt` | 762 | 视频详情页状态中枢，负责详情拉取、收藏夹归属、稍后再看与下载创建 | 聚合各仓库门面，setFullscreen 契约锁定全屏维持当前在线片源与进度，统筹选线起播、测速会话与进度保存透传时长 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesScreen.kt` | 514 | 收藏夹树状目录管理视窗，支持多选批量操纵与长按菜单 | 多选顶栏统一接入 StandardVideoActionDropdown（严格区分仅选视频、仅选目录与混合选中三态），卡片右侧删除按钮全面移除收敛至长按菜单，长按抽屉接入 StandardVideoActionMenu Zygote 基座并补齐稍后再看，弹窗按钮 4 字对齐，接入 MultiSelectTopBar 与 ZakoSearchBar |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoriteTreeItems.kt` | 238 | 收藏夹树状组件（面包屑路径条、子收藏夹卡片与视频卡片） | 呈现 BreadcrumbBar 路径芯片、FavoriteFolderItem 文件夹卡片与视频卡片，全面移除卡片右侧删除与更多按钮，收敛删除交互至长按菜单二次确认，释放标题横向宽度，接入 isLocalCached 本地缓存状态与时长角标 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesDialogs.kt` | 240 | 收藏夹业务弹窗与操作抽屉宿主 | 提供新建/重命名文件夹弹窗、重命名视频弹窗及基于 `StandardVideoActionMenu` Zygote 基座的文件夹与收藏视频长按操作抽屉，清理未使用导包 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/favorites/FavoritesViewModel.kt` | 473 | 收藏夹业务状态中枢，处理层级切换、批量移动/删除与搜索 | 调度 `FavoriteRepository` 与 `HistoryRepository` 补齐单条与批量 addToWatchLater / batchAddToWatchLater 稍后再看逻辑，原子清空 `toggleSelectAll()`，响应式监听 DownloadManager 已完成视频 ID 维护本地缓存状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeScreen.kt` | 520 | 首页主页面视窗，负责列表滚动骨架、瀑布流/网格排布与状态机调度 | 维护下拉刷新、触底分页预加载、状态机隔离，移除未启用数据源态冗余重试，抽离 HomePaginationBar，分页模式空状态下保持翻页栏常驻杜绝卡死 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeSearchBar.kt` | 324 | 首页搜索输入框、暗号直达提示与搜索历史交互面板 | 彻底移除向右箭头，复用 `ZakoSearchBar` 统一胶囊搜索框与中性色一键清空微动效，统筹搜索历史单条删除、批量删除与清空全部 AlertDialog 二次确认及 4 字按钮对齐 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeFilterBar.kt` | 459 | 首页分类标签栏、排序模式切换、视图切换与综合筛选抽屉 | 统一筛选、排序、布局切换按钮着色（布局模式保持 Tonal 浅色着色，筛选按钮着色与排序逻辑对齐，仅在非默认筛选时激活） |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeJumpPageDialog.kt` | 173 | 首页分页排布模式跳转指定页弹窗 (Material 3 AlertDialog) | 纯数字输入拦截、自动请求焦点并弹起软键盘、边界合法性校验、回车直达与即时收起 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/home/HomeViewModel.kt` | 606 | 首页状态机中枢，管理视频列表分页、搜索词历史与暗号直达 | 协同 `DataSourceRegistry` 与 `SearchHistoryRepository`，统一 1-based 分页调度，支持暗号解锁数据源插件并异步落库 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerScreen.kt` | 401 | 独立全屏播放视窗，集成控制层覆盖物、CDN 测速面板与画幅调整 | 装载 `MPVView` 纯原生渲染视口，全面复用 `PlayerTopBar`、`PlayerBottomControls`，统一「更多」菜单 4 字规范与「视频详情」 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/player/PlayerViewModel.kt` | 755 | 播放器状态机中枢，统筹起播选线、实时测速热切、进度保存与外部播放 | 驱动 `ZakoPlayer` 与 `PlaybackSourceCoordinator`，统筹本地/在线起播选线，起播与退出保存进度完整透传时长与 VIP |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/ProfileScreen.kt` | 425 | 个人中心主入口视窗 | 提供离线下载、稍后再看、源站设置（解锁可见）、数据备份与全局设置等子模块聚合导航列表，严格遵循 MD3E 双层间距规范 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/AboutScreen.kt` | 296 | 关于视窗，展示应用版本信息、开源协议与开发者鸣谢 | 调整许可声明与开源仓库上下顺序并补齐左侧图标基线对齐，导出日志独立为「诊断与排错」区块，收敛双层间距 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/about/LogViewerScreen.kt` | 366 | 实时应用日志查看器视窗 | 提供日志实时流刷新、级别筛选、关键词搜索、清空日志二次确认弹窗（4 字对齐）及外部导出分享功能，级别颜色收敛至 LogLevelColors |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreScreen.kt` | 378 | 数据备份与恢复视窗 | 规范导出备份与恢复备份 MD3E 区块标题与双层间距 Token，提供生成全量 ZIP 备份文件、导入恢复与进度反馈 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreDialogs.kt` | 373 | 数据备份与恢复相关模态弹窗集 | 承接新建备份确认、恢复提示、密码输入及完成反馈对话框，进度条与描边全量收敛至 Design Tokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/backup/BackupRestoreViewModel.kt` | 266 | 数据备份与恢复业务逻辑控制器 | 调度 `BackupRestoreManager` 执行流式序列化/反序列化，缓冲区收敛至 StorageConstants，暴露进度与完成弹窗状态 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsScreen.kt` | 402 | 离线下载管理视窗，分栏管理已下载/下载中任务，支持多选与长按菜单 | 多选顶栏统一收敛为 StandardVideoActionDropdown（包含加入收藏、稍后再看与删除文件/删除任务危险项），卡片右侧删除按钮全面移除收敛至长按菜单，长按菜单接入 StandardVideoActionMenu，接入 SingleDeleteConfirmDialog 补齐二次确认，存储路径与列表留白全量 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadTaskItems.kt` | 231 | 离线下载任务条目卡片组件（已下载卡片与下载中卡片） | 已下载卡片移除右侧删除按钮，下载中卡片移除取消按钮并保留暂停/继续控制，收敛删除操作至长按抽屉，接入 `DurationBadge` 呈现封面时长角标并优化底部统计排版 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsDialogs.kt` | 183 | 离线下载弹窗与长按抽屉宿主 | 基于 StandardVideoActionMenu Zygote 基座提供 DownloadActionBottomSheet 菜单，已下载与下载中分别挂载「删除文件」与「删除任务」危险操作（均唤起 SingleDeleteConfirmDialog），间距全量收敛至 Spacing |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/downloads/DownloadsViewModel.kt` | 354 | 离线下载业务状态中枢，负责任务流监听、暂停/继续/重下与跨目录迁移 | 注入 `HistoryRepository` 补齐 addToWatchLater 与 batchAddToWatchLater 透传视频元数据与时长，支持批量转存收藏夹，监听 `DownloadManager` 进度流，修复 `toggleSelectAll()` 判定与处理路径迁移 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryScreen.kt` | 355 | 历史记录视窗，按天隔离分组呈现历史记录，支持批量管理与长按菜单 | 多选顶栏右上角接入 StandardVideoActionDropdown（包含加入收藏、稍后再看与删除记录危险项），卡片右侧删除按钮全面移除释放标题宽度，长按菜单全面接入 StandardVideoActionMenu Zygote 基座并唤起单条删除二次确认弹窗且 4 字按钮对齐，按天分组头与间距全量 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/history/HistoryViewModel.kt` | 241 | 历史记录业务状态中枢，处理历史检索、批量删除及收藏夹转存 | 调度 `HistoryRepository` 补齐 batchAddToWatchLater 批量稍后再看逻辑，响应式监听 completedVideoIdsFlow 维护本地缓存状态，提供 `toggleSelectAll()` 原子清空 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsScreen.kt` | 245 | 全局设置主入口视窗，负责顶栏导航、分组卡片拼装与缓存清理弹窗宿主 | 严格配备「个性化与隐私」「播放与下载」「存储与日志」MD3E 区块标题，统一双层间距 Token 与 CosAV 数据源副标题 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsComponents.kt` | 327 | 设置同包共享基础组件与弹窗容器 | 提供 `SettingsCategoryItem`、`SettingsCard`、`SettingsSwitchItem`、`ExternalAppCard` 以及权限/迁移弹窗组件，几何尺寸收敛至 SettingsTokens |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/AppearanceSettingsSection.kt` | 399 | 外观与主题设置视窗，支持莫奈动态色、自定义调色盘与图标主题跟随 | 严格规范「应用图标」与「主题与配色」MD3E 区块层级与分组卡片圆角，双层间距全量 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/PlaybackSettingsSection.kt` | 253 | 播放核心引擎、长按倍速与外部播放器配置视窗 | 严格规范「手势与倍速」「内核与解码」「在线流媒体」「外部播放器」MD3E 区块标题与双层间距 Token |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/DataSourceSettingsSection.kt` | 214 | 源站设置与在线流媒体 CDN 线路选优调度配置视窗与区块 | 独立源站设置子页面，配备「视频数据源」与「流媒体线路」区块标题，更名「CosAV 数据源」，双层间距全量 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/StorageAndSecuritySettingsSection.kt` | 459 | 存储路径、文件权限、离线迁移、封面脱敏及长按分享配置视窗 | 统一文件访问权限卡片样式，标题左对齐并右置矢量状态图标（对齐下游卡片），配备「存储权限」「下载路径」「外部调用」MD3E 区块标题，双层间距 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/settings/SettingsViewModel.kt` | 428 | 全局配置业务状态机控制器 | 调度 `SettingsRepository`，抽离 ShareSettingItem 枚举与 visibleShareSettingItems/hasVisibleShareSettings 动态计算属性，响应式更新 DataStore 偏好并触发图标别名同步 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterScreen.kt` | 301 | 稍后再看视窗，展现待看列表并支持多选模式、批量管理与长按上下文菜单 | 多选顶栏右上角接入 StandardVideoActionDropdown（排除稍后再看，包含加入收藏与沉底移出待看危险项），卡片右侧删除按钮全面移除收敛至长按菜单，长按菜单全面接入 `StandardVideoActionMenu` Zygote 基座，补齐长按移出待看与批量移出 AlertDialog 二次确认弹窗且按钮 4 字对齐，统一长按「视频详情」，全量 Token 化 |
| `app/src/main/kotlin/moe/zako/viewer/ui/screens/profile/watchlater/WatchLaterViewModel.kt` | 204 | 稍后再看业务控制器与多选状态中枢 | 监听待看流与 completedVideoIdsFlow 维护本地缓存状态，统筹多选模式切换、`toggleSelectAll()` 全选/反选与批量转存收藏夹 |

---

## 9. 工程构建与 CI/CD 自动化流水线

| 文件路径 | 代码行数 | 职责与功能（做了什么） | 核心实现方式（怎么做） |
| :--- | :---: | :--- | :--- |
| `build.gradle.kts` | 5 | 根工程 Gradle 构建拓扑脚本 | 配置根级构建脚本与基础插件别名声明 |
| `settings.gradle.kts` | 18 | 模块依赖树与 Maven 插件仓库编排 | 定义工程名 `NeoViewer`，编排 Google、MavenCentral 及本地插件存储库 |
| `app/build.gradle.kts` | 151 | 核心应用模块构建参数、依赖声明与发版配置 | 配置 Android SDK 34、CMake 原生链接、R8 混淆、签名配置、base.archivesName 动态规范化输出产物命名（ZakoViewer-${versionName}-arm64-v8a-${buildType}.apk）及动态版本号递增字段（v1.3.15 / versionCode 79） |
| `.github/workflows/release.yml` | 130 | GitHub Actions 自动化发版流水线工作流 | 监听版本 Tag（`v*`），自动化构建环境，生成更新日志并自动归档发布纯源码压缩包 |