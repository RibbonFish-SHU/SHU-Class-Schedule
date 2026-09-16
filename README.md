# 上大课表 SHU-Class-Schedule

上海大学课程表 Android App：从教务系统（正方 `jwxt.shu.edu.cn`）一键导入课表，无广告、无跟踪、数据全在本地。

> 仓库工作流为 **issue 驱动**，见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 背景

- 商业课表 App 广告泛滥；[Sleepy 轻课表](https://github.com/lingion/sleepy) 等开源替代虽收录上大，但其导入需跳转教务深路径，上大这套系统对深路径/会话切换敏感，实测会被踢回登录页。
- 本 App 的导入策略：**WebView 只开教务根路径，用户手动完成统一身份认证登录后，不跳任何深链，直接在已登录页面上下文同源请求课表接口**。该路线已在 [SHU-jwxk-assistant](https://github.com/zmdld11/SHU-jwxk-assistant) 长期验证。
- 不采集、不存储任何密码；课表数据仅存本机（Room），支持 JSON 导出备份。

## 功能规划

| 里程碑 | 内容 | Issue |
|---|---|---|
| M0 | 开发环境与项目骨架 | #1 |
| M1 | 数据层 + 正方教务解析器（周次/单双周） | #2 |
| M2 | WebView 登录导入 + 预览 + 备份 | #3 |
| M3 | 周视图 UI + 学期管理 + 设置，`v0.1.0` | #4 |
| Roadmap | 桌面小组件 / 课前提醒 / ICS 导出 / CI 发版 | #5–#8 |

## 技术栈

Kotlin · Jetpack Compose · Material 3（动态色）· MVVM · Room · DataStore · Hilt · 单模块

minSdk 26 / target & compileSdk 35 / JDK 17

## 构建

```bash
# 需 JDK 17 与 Android SDK（platforms;android-35, build-tools;35.0.0）
./gradlew assembleDebug   # 产物在 app/build/outputs/apk/debug/
./gradlew test            # JVM 单元测试
```

## 许可

[MIT](LICENSE)
