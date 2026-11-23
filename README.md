# ZakoViewer

ZakoViewer 是一款基于 Kotlin 与 Jetpack Compose 构建的高性能、现代化 Android 视频客户端应用。项目深度整合底层 MPV 原生渲染管线与端侧智能决策架构，提供高可用流媒体选线、本地资产优先仲裁、语义级隐写分享及 Material 3 Expressive 极致流体视觉体验。

---

## 📊 工程源码规模统计

本项目遵循现代化 Android 与 Clean Architecture 规范精心演进，截至最新版本（**v1.3.1**）代码规模统计如下：

| 语言 / 类型 | 模块数 | 源码行数 | 核心架构与工程职责 |
| :--- | :---: | :---: | :--- |
| **Kotlin (`.kt`)** | 119 | 28,652 | 业务决策中枢、Jetpack Compose 表现层、Design Tokens、Room 响应式数据库、网络底座、下载引擎、播放器引擎适配器与 Scoped Storage 适配 |
| **C++ / JNI (`.cpp`)** | 1 | 634 | libmpv 原生硬件加速渲染、跨语言 JNI 互斥锁、异步 Seek 屏障与原生日志捕获 |
| **Gradle 构建 (`.kts`)** | 3 | 170 | 工程与模块级构建脚本、多维度依赖解耦与构建参数管理 |
| **资源配置 (`.xml`)** | 26 | 355 | 跨版本清单配置、多主题矢量图形及自适应图标资源 |
| **构建配置 (CMake)** | 1 | 32 | CMake 原生链接编排与编译参数 |
| **CI/CD 工作流 (`.yml`)** | 1 | 130 | 自动化 GitHub Release 发版与纯源码无污染归档流水线 |
| **项目规范与文档 (`.md`)** | 3 | 419 | 自动化版本递增、Commit 规范、发版发布、代码索引与编译验证准则 |
| **总计** | **154** | **30,392** | **全项目共计约 3.04 万行高质量工程源码** |

---

## ⚡ 核心特色架构与功能特性

### 1. 🚀 端侧全域本地资产优先调度与无感降级容灾 (Local-First Arbitration & Graceful Fallback)
- **单一路由中枢裁决与列表本地优先**：统一视频起播决策，历史记录、收藏夹与稍后再看三大列表统一收口于起播中枢。优先检索本地完整已下载视频，命中即**零网络阻塞、零延迟直映起播**；未命中则平滑跳转详情页；
- **详情页纯在线播放与防循环路由边界**：明确「列表本地优先」与「详情页纯在线片源」的工程边界。详情页播放严格强制走在线 CDN 路由，不适用本地优先；从详情页内嵌播放切换全屏时采用原位横屏沉浸，严格维持当前在线流媒体源、已选线路节点与播放进度，严禁触发本地已下载检测；路由跳转独立播放器时显式透传 `forceOnline = true`；本地播放界面提供「查看视频详情页」安全跳转入口，彻底杜绝从本地进入详情页后再循环重定向回本地的死锁；
- **极简透明矢量指示与无感升阶**：顶部栏采用完全透明、无底色背景的极简线性矢量标识（`OfflinePin`），色彩自然融合内容主题；本地播放期间允许随时调出 CDN 节点面板并发测速并平滑无缝热切至在线流；
- **自愈式目录扫描拓扑**：若数据库记录失效或物理文件路径变更，引擎自动调动全域下载目录扫描算法，毫秒级重定位匹配文件并自动回写数据库自愈；若文件遭外部彻底删除，静默平滑降级至在线流，绝无卡死异常；
- **异步解耦与网络封面隔离**：本地起播不等待元数据网络阻塞，详情标签与推荐后台异步静默补全；历史进度记录机制内置网络封面隔离保护，杜绝私有路径覆盖污染网络元数据。

### 2. 🌐 智能多路并发 CDN 实时竞速与在线起播调度 (Smart CDN Matrix & Playback Source Coordination)
- **共享选线起播协调门面**：抽离跨模块 `PlaybackSourceCoordinator` 统一统筹在线流媒体起播线路决策，集中管控自动选优并发测速、默认节点索引优雅回退，以及流媒体代理 URL 解析，大幅瘦身全屏播放与详情页 ViewModel；
- **毫秒级高并发实时测速**：集成 `CdnSpeedTester`，在开启选线弹窗时瞬时对多路镜像分发节点发起高并发测速，实时测量 TTFB 延迟（ms）与突发吞吐带宽（KB/s），动态降序呈现质量矩阵；
- **最优路径自适应决策**：测速完毕后毫秒级研判最优物理节点并自动应用；若用户未手动干预，自动平滑切至最佳线路；若用户在测速期间已选定目标线路，以手动指令为最高仲裁优先级；
- **断点记忆无缝热切**：切换节点时精确透传 `currentPositionMs` 维持播放连续性，实现零黑屏、零进度丢失的平滑无缝热切；
- **持久化数据源策略**：播放设置深度集成「数据源与 CDN 选线」持久化区块，支持自由切换「自动选择最优节点（推荐）」与「固定默认线路（线路 1 ~ 5）」，满足个性化网络调度诉求。

### 3. 🛡️ 播放引擎适配器架构与渲染管线 (Player Engine Adapter Architecture)
- **引擎适配器解耦与轻量门面**：抽离 `PlayerEngineAdapter` 接口，将 `ZakoPlayer` 重构为轻量门面（~350 行），将底层 `libmpv` 原生渲染管线（`MpvPlayerEngine`）与系统 `MediaPlayer` 异步管线（`SystemPlayerEngine`）彻底解耦，并建立带身份校验的 `EngineCallback` 彻底隔离旧引擎迟到回调；
- **Seek 瞬态防抖与零值抑制**：在引擎适配器与播放门面建立双层 Seek 保护窗口期，抑制底层解复用重定位瞬态上报的 0 进度与残留脏数据，确保进度条拖动与点击跳转精准流畅；主线程同步断开旧引擎 Surface 占用，杜绝跨引擎切换时的 EGL 创建冲突与双重起播异步竞态；
- **libmpv 原生硬件加速**：基于高性能 libmpv 底层驱动音视频管线，支持 GPU 渲染输出直通与多格式硬件解码加速；
- **Surface 引用缓存与起播零冗余绑定**：维护活动 Surface 实例，起播与切片完全跳过重复的 EGL 重建与无效尺寸设置；严格遵循 MPV 现代规范弃用旧版 `video-aspect-override` 整数指令，保障起播即刻渲染并完全符合现代 MPV 规范；
- **JNI 互斥锁与生命周期同步**：底层跨语言交互引入互斥锁（Mutex）与异步 Seek 屏障，严密协同 Surface 生命周期，杜绝引擎热切换与跨线程竞争崩溃；
- **系统内核全异步串行 IPC**：系统 `MediaPlayer` 的全部生命周期与 Surface 解绑操作统一收敛至独立单线程队列串行派发，彻底消除主线程阻塞与线程泄漏；
- **本地微型反向代理网关**：内置支持流式 HTTP Range 请求的专用本地代理网关，高效适配原生播放内核与网络分片拉取。

### 4. 🔏 语义级 SFW 汉字隐写编解码分享协议 (Steganographic SFW Cipher Protocol)
- **全自然语意混淆映射**：业内首创将复杂特征定位符隐写编码为无任何违禁词的纯中文自然汉字暗号，杜绝外链敏感拦截与社交平台封禁风险；
- **端侧毫秒级快速逆解**：直接在搜索栏粘贴或输入汉字暗号，词法解析器自动捕获并在毫秒内精准定位直达对应内容；
- **隐私防社死体系与彩蛋机制**：全局封面智能高斯模糊开关，随时防范公共场合视觉泄露；关于页多击隐藏解锁机制，开启后支持长按分享无损提取完整资源定位符。

### 5. 🎨 Material 3 Expressive 动态流体美学与自适应响应式拓扑 (M3 Expressive Fluid Design)
- **全局基础 Design Token 与常量底座建立**：建立 `Dimens`（语义化间距 `Spacing`、圆角 `CornerRadius`、图标尺寸 `IconSize`、触控目标 `TouchTarget`、标高 `Elevation` 与通用封面规格）、`MotionTokens`（语义化动效时长与缓动曲线）及 `Typography`（全层级文字排版与业务角标扩展样式），将局部 Token 统一对齐；建立 `StorageConstants` 统筹系统公共导出子目录与流式 I/O 缓冲区规格；
- **全域搜索框自动失焦与状态感知**：在 `ZakoSearchBar` 中内建严密的软键盘展开收起监听判定（`wasImeVisible && !isImeVisible`）与搜索提交失焦机制，并抽离出可复用的 `clearFocusOnScroll` 与 `clearFocusOnTap` 修饰符，全面接入首页、收藏夹、历史记录与应用选择器，实现顺畅自如的失焦与浏览体验；
- **全域搜索框组件与带微动效一键清空归一**：抽离 `ZakoSearchBar` 与 `SearchBarTokens`，全应用（首页搜索条、收藏夹内搜索、历史记录搜索及应用选择器）统一 28dp 胶囊轮廓、中性前景色（`onSurfaceVariant`，禁止随主题色变色）与图标规格。彻底移除右侧向右箭头，替换为一键清空按钮，配合 `AnimatedVisibility` 平滑进出过渡，并在点击瞬间触发缩放回弹与轻度旋转的触感微动效；
- **首页筛选、排序与布局切换圆形按钮着色统一**：抽离 `FilterBarCircleButton` 与 `FilterBarTokens`，统一采用 Material 3 Tonal 逻辑（`secondaryContainer` 底色 + `onSecondaryContainer` 图标色），并在激活态（勾选综合筛选条件或非默认排序）统一切换为高对比主题色（`primary` 底色 + `onPrimary` 图标色），彻底消除视觉割裂与间距硬编码；
- **全链路 1-based 分页拓扑与防抖限流自愈**：统一全端页码基准（杜绝 `page=0` 异常请求），网络请求接入可取消协程（`suspendCancellableCoroutine` + 底层 Socket 物理连接立即熔断），并在 ViewModel 层建立 400ms 相同参数并发请求拦截屏障；首次推荐加载即并发拉取列表总数初始化准确的 `totalPages` 跳转范围，当跳转超界导致空列表时底部分页栏（上一页/第 X 页/下一页）保持常驻杜绝界面卡死；精确解析服务端业务响应码（如 HTTP 200 下返回的 429 频繁风控），透传明确错误态与重试指引；
- **系统级莫奈动态取色**：支持随 Android 12+ 动态壁纸主色实时流动重绘，并内置紫色（`#686BDD`）、浅绿、淡黄及无级自定义调色板；
- **全域横向视频列表卡片收口归一与本地缓存标识**：抽离全局统一组件 `VideoListItemCard`，合并收藏夹、离线下载、历史记录及稍后再看 4 大列表镜像卡片结构，统一由 `VideoCoverTokens`（120x68dp）、`Dimens` 与主题色驱动，全量集成隐私模糊、`DurationBadge` 封面时长角标、`isLocalCached` 本地已缓存标识、多选状态机、副信息插槽及尾部操作区；离线下载任务条目优化排版，将实时速率移至底部统计行与已下/总大小并列展示杜绝截断；
- **分享设置动态可见性判定与圆角自适应**：为分享设置抽离 `ShareSettingItem` 与 `visibleShareSettingItems` 集合，避免将入口硬绑定到单一布尔值上；动态汇总所有可见配置项，为空时在「我的」页与设置主页优雅隐藏「分享设置」入口并自适应相邻项底部大圆角；
- **目录迁移弹窗与生命周期存储权限监听收敛**：抽离 `MigrationProgressDialog` 统一离线下载与全局存储设置的跨目录迁移确认及进度展示；抽离可复用 Compose 副作用组件 `ObserveStoragePermissionOnResume`（利用 `rememberUpdatedState` 彻底防止捕获过期闭包），统一下载、设置、备份恢复及目录浏览器的生命周期权限恢复监听；收藏夹文件夹抽屉全面收口复用 `VideoActionBottomSheet` 与 `MenuTokens`；
- **日志查看器搜索一致性与备份恢复视窗瘦身解耦**：实时日志页 `LogViewerScreen` 接入全局统一胶囊搜索框 `ZakoSearchBar` 并挂载 `clearFocusOnScroll` 与 `clearFocusOnTap`，实现关键词快速定位与一致的自动失焦体验；拆分 `BackupRestoreScreen`（从 667 行瘦身至 323 行），将导入/导出进度、恢复结果及成功分享弹窗完整解耦至同包 `BackupRestoreDialogs`；
- **全域图标与上下文菜单 Design Token 归一**：建立 `AppIcons` 语义化图标 Token 与 `MenuTokens` 菜单样式规范，以「离线下载」多选图标（Checklist）作为全应用唯一基准，历史记录、我的收藏与稍后再看三大列表全面对齐；基于 `VideoActionBottomSheet` 抽离高度复用的长按上下文操作菜单，驱动离线下载、历史记录、我的收藏与稍后再看四大核心页面在圆角、标高、内边距、图标尺寸与动效上实现像素级视觉一致，全链路打通单项添加到层级收藏夹，并为稍后再看补全多选模式、批量转存收藏夹与批量移出的统一交互；
- **全域播放组件归拢与共享控制拓扑**：建立 `ui/components/player/` 播放器专用组件库，抽离共享播放顶栏（`PlayerTopBar`）、波浪进度控制条（`PlayerBottomControls`）、CDN 测速选线抽屉（`CdnNodeSelectorDialog`）及播放内核与硬解切换弹窗，驱动独立全屏播放视窗与视频详情内嵌播放器实现高度复用与像素级对称；
- **全域时长角标与本地文件信息规范归一**：抽离 `DurationBadge` 封面时长角标组件与解析器，统一兼容浮点秒、整数秒、毫秒与格式化串，全面覆盖离线下载、历史记录、收藏夹与稍后再看四大列表；合并本地文件信息弹窗为单一公共组件 `LocalFileInfoDialog`，长按菜单统一固定「查看详情」跳转详情页，检测到本地视频时无缝呈现「文件信息」；
- **全仓 Design Token 覆盖与硬编码绝对清零**：全仓（涵盖个人中心、6 大设置模块、日志查看器、备份恢复、离线下载、历史记录、稍后再看与收藏夹树状体系）彻底消除散落硬编码。所有图标引用 100% 收敛至全局 `AppIcons` 门面（全仓业务与组件代码直接引用 `Icons.`/`ZakoIcons.` 数量实现绝对清零）；诊断日志级别色与控制台背景收敛至 `LogLevelColors` 并自适应深浅色模式，全仓散落裸写 `Color(0x...)` 数量归零；排版文字与几何尺寸全量对齐 `Typography` 与 `Dimens`（`Spacing`、`CornerRadius`、`IconSize`、`BorderWidth` 及 `SettingsTokens` / `ProfileTokens` 等）。
- **多选反选状态机原子联动**：统一 4 大列表 ViewModel 的 `toggleSelectAll()` 反选状态机逻辑，支持一键全选与原子清空，精准双向联动 `MultiSelectTopBar` 计数与勾选视觉态；
- **全域播放控制层统一几何拓扑与视觉等重补偿**：抽离 `PlayerControlDefaults` 规范中枢，全域图标标准归一至 24dp、触控容器归一至 40dp；标定自定义矢量图安全区（20x20，线宽 2.0f），并对斜向异形图标实施视觉减重补偿，实现跨全屏、竖屏与内嵌态的完全像素级和谐对齐；
- **全场景通用倍速选择视口与手势穿透隔离**：抽离 `PlaybackSpeedDialog` 统一倍速控制流，根据屏幕方向动态自适应约束视口高度，支持丝滑垂直滚动与平滑居中定位，并在交互层阻断垂直拖动手势穿透，杜绝误触底层亮度和音量调节；
- **全场景连续手势交互与跨会话平滑调控**：重构 `PlayerGestureDetector` 与 `PlayerBrightnessVolumeHelper` 统一手势决策中枢，覆盖横屏全屏、竖屏全屏及详情页原位内嵌三大形态。起手动态直读窗口属性与设备真实系统亮度/媒体音量，杜绝闭包过期与手势跳变；拖拽过程阶梯与浮点双向平滑归一化，退出播放页自动释放窗口亮度覆盖跟随系统，提供丝滑可靠的视听交互流；
- **检索与切换状态机严格隔离防闪烁**：重构标签切换与布尔语法检索的状态流转，发起新请求即刻激活居中 Material 3 流体加载动画，严格隔离空状态渲染时机，内置协程并发取消与静默退出屏障，彻底杜绝数据穿透闪烁与静默等待假死感；
- **桌面图标动态随动映射**：应用图标颜色实时响应主题变动，重启即刻生效；
- **大屏平板响应式双栏排布**：横屏自适应切分黄金视觉双栏（左侧原生渲染播放与交互，右侧独立滚动推荐瀑布流），侧边栏（NavigationRail）自适应平滑折展；
- **Expressive 可动波浪阻尼滑块**：播放控制集成 Material Design 3 Expressive 弹性波浪进度组件，赋予音画交互细腻的高级动效触感。

### 6. 📁 无限递归树形元数据拓扑架构 (Infinite Hierarchical Asset Tree)
- **突破扁平限制**：基于 Room 关系模型构建支持无限深度的多叉树目录结构，支持无限嵌套子目录分类；
- **工业级批量操纵**：支持跨层级批量多选移动、重命名、复制与快速检索，内置高度封装的树状文件夹拾取器（`FolderTreeDialog`）。

### 7. 💾 Scoped Storage 公共空间零妥协直出与全流程防打断安全屏障 (Zero-Compromise Scoped Storage)
- **公共存储免 Root 直达与跨版本日志物理隔离**：备份归档（`Download/ZakoViewer/Backups/`）与诊断日志（`Download/ZakoViewer/Logs/`）深度适配 Android 10+ `MediaStore.Downloads` 与低版本外部存储；日志系统内置编译期构建时间戳与全链路版本标识，冷启动自动感知版本跃迁并原子归档旧版日志，导出自动注入软硬件诊断头；
- **防误触中断与全局唤醒锁（WakeLock）**：大体积视频备份导出与导入恢复期间，自动激活不可关闭阻断层与 `FLAG_KEEP_SCREEN_ON` 常亮保护，彻底阻止休眠或返回键中断数据写入；
- **分阶段流式解压与结果卡片**：毫秒级进度回调（已读字节数、百分比、封面/视频解压数精准计数），恢复完毕结构化卡片汇总资产数据，并在 `try/finally` 中严格清理临时缓存，杜绝存储膨胀。

### 8. ⚡ 全局代码复用收敛与标准库优先迁移 (Stdlib-First & Network Unification)
- **权威共享网络底座 (NetworkClientProvider)**：全项目收敛单一权威 OkHttpClient 提供者与连接池复用架构，为 API 请求、封面缓存、多路测速、分片断点续传及流媒体代理等提供专用派生客户端，杜绝重复创建客户端导致的套接字与线程泄漏，统一注入带 Kotlin Contracts 的 `isHttpUrl()` 契约校验；
- **标准库优先（Stdlib-First）**：全仓替换手写流式缓冲样板代码与自造轮子，ZIP 打包/解压、封面缓存、物理目录迁移等 I/O 读写全面迁移至标准库 `input.copyTo(output, bufferSize)` 与 `use` 作用域；字符串与字节编码统一采用原生 `Charsets.UTF_8`；
- **全域字节大小格式化收敛**：在 `StorageConstants` 确立单一权威 `formatBytes(bytes: Long)` 入口，彻底消除公共存储、文件详情与设置页中的重复格式化算法；
- **依赖精简与通配符导入清零**：彻底移除零引用的 `logging-interceptor` 依赖及版本配置；全仓严格执行精确显式导包红线，通配符导入（`*`）实现绝对清零。

### 9. 🔌 内置数据源插件化解耦与隐藏暗号解锁体系 (Pluggable DataSource & Secret Unlock Architecture)
- **统一数据源插件化标准抽象**：在 `data/source/` 建立标准插件契约 `VideoDataSource` 与全局注册中心单例 `DataSourceRegistry`，将原本紧耦合的业务接口封装为首个内置实现 `CosAvDataSource`（`id = "cosav"`）。首页、详情、播放器选线、下载与封面缓存全面解耦对底层 API 客户端的直连，实现业务表现层与数据源实现的完全隔离；
- **全域优雅兜底与本地能力零依赖保障**：在无任何可用数据源激活时，首页不发起任何无效网络请求，展示明确友好的空状态；离线下载、本地收藏、历史播放与数据备份等本地功能保持 100% 独立可用；
- **版本号 Base64 动态暗号解锁与防泄漏拦截**：在全局搜索解析链路最前端，动态匹配当前应用版本号（`BuildConfig.VERSION_NAME`）的 UTF-8 标准 Base64 暗号（如 `1.3.0` -> `MS4zLjA=`）。触发解锁后自动置位持久化开关并即刻清空搜索框，严密拦截暗号写入搜索历史数据库，保障隐私安全；
- **新装防敏感隐藏与历史升级老用户平滑豁免**：新装用户默认隐藏数据源配置卡片且内置插件处于未激活态；升级检测机制自动识别历史存量用户，首次初始化自动豁免并预先激活内置插件，保障老用户升级无感平滑；
- **数据源设置模块解耦与动态可见性**：抽离 `DataSourceSettingItem` 枚举与 `hasVisibleDataSourceSettings` 动态集合判定，数据源分组无可见项时自动隐藏主菜单「数据源设置」入口，并在解锁后动态呈现内置插件开关与 CDN 测速选线配置。

### 10. ⏱️ 全域视频时长静默自愈与多级补齐引擎 (Silent Video Duration Auto-Healing & Backfill Matrix)
- **多表源头写入补齐与跨表原子更新**：全面排查收藏夹、稍后再看、历史记录及离线下载入库链路，杜绝存量视频时长丢失；通过 Room 数据库事务提供跨表原子回写能力，单点补齐即刻同步刷新包含同一 `videoId` 的全部业务表；
- **三级降级自愈流水线 (Cross-Table -> Local Parsing -> Remote API)**：针对存量时长为空或为 0 的条目，启动时通过 `VideoDurationManager` 启动低并发后台静默补齐：
  1. *优先级 1 (跨表关联)*：优先跨其余 3 张表检索并直接提取已存在的有效时长秒级回填；
  2. *优先级 2 (本地视频解析)*：匹配本地完整已下载视频，使用 `MediaMetadataRetriever` 提取流媒体 `METADATA_KEY_DURATION` 并秒级格式化回填；
  3. *优先级 3 (远端接口回填)*：若存在已激活的有效数据源，基于 `Semaphore(2)` 严格限流与防抖间隔调用详情接口获取时长并入库；
- **Room 响应式 Flow 自动驱动 UI 刷新**：回写数据库后即刻由 Room 响应式数据流自动驱动全部列表 UI 实时展示右下角时长角标（`DurationBadge`），无需用户手动点击进入播放或详情页。

### 11. 🔒 播放器防误触锁定智能超时隐现与关于页开源直达 (Smart Lock Auto-Hiding & Repo Navigation)
- **屏幕锁定时状态图标自动隐藏与唤醒**：修复防误触锁屏模式下锁定按钮常驻屏幕遮挡视线的体验缺陷，锁定图标与播放控件超时自动渐隐；锁屏状态下点击屏幕仅临时唤醒操作按钮，超时无操作再次自动淡出，保持纯净视听体验；
- **关于页布局优化与开源仓库原生跳转**：优化关于页面卡片排列逻辑，调整日志导出层级至版本卡片下方；新增基于原生 Android Intent 的「开源仓库」一键跳转卡片，无缝直达 GitHub 开源仓库。

---

## 🛠️ 技术栈与依赖架构

- **核心语言**：Kotlin 1.9+, C++ 17 (JNI 跨语言绑定)
- **UI 体系**：Jetpack Compose (Material 3 Expressive, Edge-to-Edge Fluid Layout)
- **底层媒体内核**：libmpv (原生硬件加速渲染管线)
- **响应式数据流**：Kotlin Coroutines & Flow (SupervisorJob, 异构调度)
- **本地存储引擎**：Room Database (SQLite 递归拓扑), Jetpack DataStore Preferences
- **存储权限与沙盒**：Android MediaStore API (Scoped Storage), SAF
- **网络通信**：OkHttp 4 (连接复用与高并发并发探针), Gson

---

## 📂 核心目录架构

```text
app/src/main/
├── cpp/                           # C++ JNI 模块 (CMakeLists.txt, mpv_jni.cpp 核心互斥锁与渲染绑定)
├── kotlin/moe/zako/viewer/
│   ├── core/                      # 核心基建与能力驱动
│   │   ├── cdn/                   # CDN 动态并发测速引擎与在线选线调度中枢 (CdnSpeedTester, CdnSmartRouter, PlaybackSourceCoordinator)
│   │   ├── cover/                 # 核心业务封面持久化与按需补全引擎 (CoverCacheManager)
│   │   ├── download/              # 多并发分片断点续传下载引擎、物理目录迁移与本地检测仲裁 (DownloadManager, DownloadMigrationHelper)
│   │   ├── external/              # 外部播放器与多态应用调用适配
│   │   ├── icon/                  # 动态桌面图标动态切换管理
│   │   ├── logger/                # 结构化日志追踪、公共导出与原子截断清空
│   │   ├── network/               # 全局共享网络底座与客户端派生中枢 (NetworkClientProvider)
│   │   ├── permission/            # 存储与 Scoped Storage 权限处理适配
│   │   ├── player/                # 播放引擎适配器架构 (MpvPlayerEngine, SystemPlayerEngine, ZakoPlayer 门面)
│   │   ├── search/                # 复合词法解析引擎
│   │   ├── share/                 # SFW 语义级汉字隐写编解码核心
│   │   └── storage/               # 公共 Downloads 空间适配器 (PublicStorageHelper)
│   ├── data/                      # 统一数据持久化与外部服务模型
│   │   ├── backup/                # 数据库与多媒体归档导入导出、分阶段流式解压与恢复管理器
│   │   ├── local/                 # Room 数据库实体、DAO 接口与迁移
│   │   ├── repository/            # 数据仓库层 (HistoryRepository, SettingsRepository, FavoriteRepository)
│   │   └── source/                # 外部 API 通信与实体反序列化
│   └── ui/                        # Compose 声明式表现层
│       ├── components/            # 高级复用组件 (视频卡片、封面时长角标 DurationBadge、本地文件信息弹窗等)
│       │   └── player/            # 播放器共享组件库 (顶部栏、波浪控制条、手势 HUD、倍速视口、CDN测速选线与内核弹窗)
│       ├── navigation/            # 响应式导航拓扑与自适应多端路由
│       ├── screens/               # 各核心业务页面 (Player, Home, Detail, Profile, Settings 等)
│       └── theme/                 # 全局 Design Tokens：语义化图标 (AppIcons)、尺寸间距 (Dimens)、动效 (MotionTokens) 与主题色彩体系
└── res/                           # 资源定义 (矢量图形资源、多主题应用图标、多语言字符映射)
```

---

## 📦 本地编译与构建指南

### 1. 编译环境要求

- **JDK**：OpenJDK 17
- **Android SDK**：compileSdk 34, minSdk 26, targetSdk 34
- **Gradle**：8.10+ (推荐使用项目自带 `gradlew.bat`)
- **NDK**：Android NDK r25+ (需支持 C++17 编译)

### 2. 双版本验证构建命令

依据本项目严格的工程交付准则，改动源代码后需执行双版本编译验证：

```powershell
# 验证 Debug 与 Release 双版本自动化编译通过
.\gradlew.bat assemble --no-daemon --console=plain
```

---

## 📜 开源许可

本项目核心播放模块及底层 JNI 架构衍生自 GPLv3 开源生态（NextPlayerCloud / NeoPlayer），根据 GNU 强互惠（Copyleft）许可证条款，本项目整体遵循 [GNU General Public License v3.0 (GPLv3)](LICENSE) 协议开源。
