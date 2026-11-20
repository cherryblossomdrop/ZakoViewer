# ZakoViewer

ZakoViewer（包名：`moe.zako.viewer`）是一款基于 Kotlin 与 Jetpack Compose 构建的现代化 Android 视频客户端应用。

## 功能特性

- **Material 3 Expressive 界面与主题系统**
  - 支持系统莫奈（Monet）动态取色开关；
  - 关闭动态取色后，支持自定义主题色彩：紫色（`#686BDD`）、浅绿色、淡黄色；
  - 支持应用桌面图标颜色跟随主题色切换（重启应用生效）。
- **隐私防社死保护**
  - 视频封面缩略图默认开启模糊遮罩；
  - 支持在「我的 → 设置 → 隐私」页面随时一键开启或关闭遮罩。
- **SFW 汉字暗号分享与彩蛋机制**
  - 详情页短按分享按钮：生成纯自然中文无敏感词暗号，在搜索框输入回车即可直达对应内容；
  - 彩蛋机制：在「我的 → 关于 APP」中连续点击版本号 5 次可解锁隐藏开关，开启后长按分享图标可直接复制完整视频链接。
- **播放器与下载增强**
  - 集成 `libmpv`（基于 C++ JNI）底层高性能原生播放器；
  - 多节点并发分片下载管理器；
  - 内置本地文件夹树形选择组件（FolderTreeDialog / FolderBrowserDialog）；
  - 支持外部播放器与下载器应用列表选择调用；
  - 适配 Android 全文件管理权限（MANAGE_EXTERNAL_STORAGE）。
- **多维筛选与排序**
  - 支持多维度条件组合过滤；
  - 提供一键过滤/隐藏 VIP 视频开关；
  - 支持按热度、更新时间等多维度进行正序与倒序排序。

## 技术栈

- **开发语言**：Kotlin, C++ (JNI)
- **UI 框架**：Jetpack Compose (Material 3 Expressive)
- **本地存储**：Room Database, Jetpack DataStore Preferences
- **网络与解析**：OkHttp 4, Gson
- **图片加载**：Coil Compose
- **媒体播放**：MPV C++ JNI 绑定

## 核心目录结构

```text
app/src/main/
├── cpp/                           # C++ JNI 模块 (CMakeLists.txt, mpv_jni.cpp)
├── kotlin/moe/zako/viewer/
│   ├── core/                      # 核心模块
│   │   ├── cdn/                   # CDN 测速与节点管理
│   │   ├── download/              # 多并发分片下载
│   │   ├── external/              # 外部播放器与下载器调用适配
│   │   ├── icon/                  # 动态桌面图标替换管理
│   │   ├── logger/                # 本地与运行日志组件
│   │   ├── permission/            # 存储及全文件权限处理
│   │   ├── player/                # MPV 播放器封装
│   │   └── share/                 # SFW 汉字暗号编解码
│   ├── data/                      # 数据持久化与网络模型
│   │   ├── backup/                # 数据库导入导出与备份
│   │   ├── local/                 # Room 数据库、DAO 与数据实体
│   │   ├── repository/            # 数据仓库层
│   │   └── source/                # 数据源与网络交互
│   └── ui/                        # Compose UI 表现层
│       ├── components/            # 通用组件（文件选择树、弹窗、遮罩等）
│       ├── navigation/            # 页面导航图
│       ├── screens/               # 各功能页面（Home, Detail, Player, Profile 等）
│       └── theme/                 # 主题配色与字体排版
└── res/                           # 资源目录（矢量图、多主题应用图标、多语言字符）
```

## 本地编译与构建指南

### 1. 环境要求

- JDK 17
- Android SDK (compileSdk 34, minSdk 26, targetSdk 34)
- Gradle 8.4+（或使用项目自带的 Gradle Wrapper）
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
# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK (需先配置 keystore.properties)
./gradlew assembleRelease
```

## 开源许可

本项目基于 [MIT License](LICENSE) 协议开源。

