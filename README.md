# 上大课表 SHU-Class-Schedule

上海大学课程表 Android App：从教务系统（正方 `jwxt.shu.edu.cn`）一键导入课表，无广告、无跟踪、数据全在本地。

> 仓库工作流为 **issue 驱动**，见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 下载安装

从 [Releases](https://github.com/zmdld11/SHU-Class-Schedule/releases) 下载最新 `shu-schedule-vX.Y.Z.apk` 安装。

- **v0.3.2 起使用固定签名**：从 v0.3.2 升级到更新版本可直接覆盖安装；从更早版本升级因签名切换需先卸载（卸载前在 设置 → 备份与恢复 里导出 JSON，装好后恢复）。
- 密码只进学校官方登录页，本应用不保存；课表数据仅存本机。

## 背景

- 商业课表 App 广告泛滥；[Sleepy 轻课表](https://github.com/lingion/sleepy) 等开源替代虽收录上大，但其导入需跳转教务深路径，上大这套系统对深路径/会话切换敏感，实测会被踢回登录页。
- 本 App 的导入策略：**WebView 只开 SSO 网关，用户手动完成统一身份认证后，再进入课表查询页读取教务自己的学年/学期编码，同源请求课表接口**——学期编码（xqm）以教务页面真实下拉值为准，不做硬编码假设。该路线源自 [SHU-jwxk-assistant](https://github.com/zmdld11/SHU-jwxk-assistant) 的长期验证。
- 不采集、不存储任何密码。

## 功能

- **导入**：教务 SSO 登录 → 自动读取可选学年/学期（真实编码，春秋夏均可抓取）→ 预览核对 → 写入本地
- **调课消解**：系统里「原教师/现教师」并存时默认保留原教师的常规记录，避免调课信息污染日常显示（#18）
- **周视图**：同课同色、今日高亮、周次跳转；默认只显示工作日 5 列（可开周末）；非本周课程可置灰显示；块内 课名/@教室/教师/校区 分行展示
- **课程编辑**：手动加课、改时间地点教师、增删时段与整门课——教务数据与实际不符时自己改（#13）
- **学期管理**：顶栏下拉快捷切换；手动添加空白学期；冬季学期默认隐藏（教务查不到数据，设置可开）（#14/#16）
- **桌面小组件**：2×2 下一节课 / 4×2 / 4×4 今日课程，点击打开应用
- **节次与显示**：12 节默认作息可改、可恢复默认；节次结束时间开关
- **备份**：JSON 全量导出/恢复
- **CI/CD**：push 单测构建；打 `v*` tag 自动出固定签名 release APK

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
