# 项目开发规范与版本构建准则

无论用户在对话中是否显式提及，只要在本项目目录下执行任何开发或文件改动，必须严格遵守以下规则：

## 1. 版本号递增规则（仅限源代码改动）
- **触发条件**：**只有改动了应用源代码**（例如 `app/src/` 目录下的 Kotlin、C++、JNI、XML 资源文件、清单文件，或影响最终应用二进制产物的 Gradle 构建依赖配置）并进行功能新增或 Bug 修复时，才允许且必须递增版本号。
- **禁止递增**：纯文档修改（如 `README.md`）、开发规范文件修改（如 `AGENTS.md`、`GEMINI.md`）、配置说明或非源码文件的调整，**严禁修改版本号**。
- **递增方式**：
  - 读取 `app/build.gradle.kts` 中的 `defaultConfig` 配置。
  - 将 `versionCode` 自动加 `1`。
  - 将 `versionName` 顺延递增一个版本号（例如由 `1.2.13` 递增至 `1.2.14`）。

## 2. 执行双版本编译构建验证（仅限源码改动）
- 当且仅当改动了应用源代码且递增了版本号后，必须执行以下命令验证 Debug 与 Release 双版本编译通过：
  ```powershell
  .\gradlew.bat assemble --no-daemon --console=plain
  ```
- **严禁**添加 `--stacktrace` 参数。
- 必须确认 `:app:assembleDebug` 与 `:app:assembleRelease` 均编译成功（`BUILD SUCCESSFUL`）后方可交付。

## 3. 自动提交并推送到 GitHub 远程仓库
- **触发条件**：每次改动了项目文件（**即使没有改变应用源代码**，例如更新了 `README.md`、开发规范文档等），在修改确认无误后，必须自动执行 Git 提交（`git add`、`git commit`）并推送到 GitHub 远程仓库（`git push origin main`）。
- **敏感文件例外**：若改动的文件属于敏感凭证或私有配置（例如 `keystore.properties`、私钥证书 `.jks`/`.keystore`、本地 SDK 路径 `local.properties` 等隐私安全文件），严禁推送到远程仓库。
