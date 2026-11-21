# 项目开发规范与版本构建准则

无论用户在对话中是否显式提及，只要在本项目目录下执行任何开发或文件改动，必须严格遵守以下规则：

## 1. 版本号递增规则（仅限源代码改动）
- **触发条件**：**只有改动了应用源代码**（例如 `app/src/` 目录下的 Kotlin、C++、JNI、XML 资源文件、清单文件，或影响最终应用二进制产物的 Gradle 构建依赖配置）并进行功能新增或 Bug 修复时，才允许且必须递增版本号。
- **禁止递增**：纯文档修改（如 `README.md`）、开发规范文件修改（如 `AGENTS.md`）、配置说明或非源码文件的调整，**严禁修改版本号**。
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

## 3. 自动提交并推送到 GitHub 远程仓库与 Commit 规范
- **触发条件**：每次改动了项目文件（**即使没有改变应用源代码**，例如更新了 `README.md`、开发规范文档等），在修改确认无误后，必须自动执行 Git 提交（`git add`、`git commit`）并推送到 GitHub 远程仓库（`git push origin main`）。
- **敏感文件例外**：若改动的文件属于敏感凭证或私有配置（例如 `keystore.properties`、私钥证书 `.jks`/`.keystore`、本地 SDK 路径 `local.properties` 等隐私安全文件），严禁推送到远程仓库。
- **Commit 提交类型严格区分（严禁混用、自创或错写）**：
  - `feat`: 仅限新功能开发或功能增强；
  - `fix`: 仅限 Bug 修复与缺陷排障；
  - `docs`: 仅限文档补充或说明调整（如 `README.md`）；
  - `ci`: 仅限 CI/CD 自动化流水线配置与脚本调整（如 `.github/workflows/`）；
  - `refactor`: 仅限代码结构重构（既不新增功能也不修复 Bug）；
  - `style`: 仅限不影响业务逻辑的格式化或视觉像素级微调；
  - `chore`: 仅限项目规范文件（如 `AGENTS.md`）、构建辅助配置或杂项变动。
  - 提交必须严格匹配改动实质，严禁将文档或 CI 改动标为 `feat`/`fix`，确保自动化 Release 提取真实可靠的应用更新日志。

## 4. 项目文档同步规则
- **主动评估与同步**：今后每次进行代码改动后，必须主动评估本次改动（新增特性、架构调整、功能变更等）是否影响项目文档准确性，视情况同步更新根目录 `README.md`。
- **内容风格与严谨性**：更新 `README.md` 时重点介绍核心与特色能力，行文精炼高级，并及时更新代码规模等动态数据，确保文档真实、准确地反映项目最新状态。

## 5. 发版与 GitHub Release 规范
每次执行发版并推送到 GitHub 时，须按以下顺序操作：
1. 确认 `versionCode` / `versionName` 已按本次发版更新，并按文档同步规则视情况更新 `README.md`。
2. 确认工作区不包含 `.apk` 或敏感配置文件后，提交改动并推送当前分支到 GitHub。
3. 基于最新提交创建并推送对应的语义化版本 Tag（如 `git tag vX.Y.Z && git push origin vX.Y.Z`），由 GitHub Actions 自动创建对应版本的 Release 并生成更新日志（GitHub 官方将自动附带纯净源码包，严禁额外编译或上传 APK）。

