# 项目开发规范与版本构建准则

无论用户在对话中是否显式提及，只要在本项目目录下完成任何代码修改、功能新增或 Bug 修复后，必须自动执行以下版本递增与编译验证流程：

## 1. 自动递增版本号
- 读取 `app/build.gradle.kts` 中的 `defaultConfig` 配置。
- 将 `versionCode` 自动加 `1`。
- 将 `versionName` 顺延递增一个版本号（例如由 `1.2.5` 递增至 `1.2.6`）。

## 2. 执行双版本编译构建验证
- 修改全部就绪且版本号递增后，必须执行以下命令验证 Debug 与 Release 双版本编译通过：
  ```powershell
  .\gradlew.bat assemble --no-daemon --console=plain
  ```
- **严禁**添加 `--stacktrace` 参数。
- 必须确认 `:app:assembleDebug` 与 `:app:assembleRelease` 均编译成功（`BUILD SUCCESSFUL`）后方可交付。
