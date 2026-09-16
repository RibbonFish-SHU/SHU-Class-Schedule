# 协作规范（CONTRIBUTING）

> 本仓库的核心工作流：**issue 驱动**。任何修改（新功能、重构、修 bug、写文档）都从一条 GitHub issue 开始。

## 一、修改流程（必须遵守）

```
建 issue → 逐项完成（过程记录评论）→ 写小结 → 关闭 issue → 合并 main
```

### 1. 先建 issue（一个修改计划 = 一个 issue）

动手改代码**之前**先建 issue，写清：

- **目标**：这次要改成什么样
- **范围**：动哪些模块/文件，不动什么
- **验收标准**：怎么算做完（功能演示点、测试等）

issue 标题用动词开头，如 `实现 WebView 登录导入流程`。
Roadmap 类暂不开发项打 `roadmap` 标签占位，认领后再细化方案。

### 2. 过程记录在 issue 里

实现过程中遇到的**任何卡点、设计取舍、意外发现**，及时以**评论**形式追加到对应 issue 下。

### 3. 关闭前写小结

完成时在 issue 里追加一条**小结评论**，固定格式：

```markdown
## 小结
- 改动内容：（列点，对应 issue 目标逐条勾销）
- 关键文件：`app/src/main/...` 等
- 踩坑记录：（无则写"无"）
- 遗留问题：（无则写"无"）
```

然后关闭 issue。

## 二、分支与合并

- `main` 分支**随时可构建、可运行**，禁止提交编译不过的代码
- 每个 issue 开一个短分支：`feat/import-webview`、`fix/week-parser` 等
- 完成后合回 `main`（本地 merge 后 push 或 PR 均可）；merge 前自测通过
- 里程碑完成打 tag：`v0.1.0` …

## 三、Commit 规范

Conventional Commits，**必须引用对应 issue 号**：

```
feat(import): 实现 WebView 登录与课表抓取 #3
fix(parser): 修复单双周解析漏周 #2
docs: 补充构建说明 #1
chore: 升级 compose BOM #6
```

类型：`feat` / `fix` / `docs` / `refactor` / `test` / `chore`。一个 issue 通常对应 1~5 个 commit，每个 commit 保持可编译。

## 四、角色分工

- **仓库主管（owner）**：建库、权限、main 保护、issue 分派与验收。
- **AI 助手（ZCode）**：受主管委托执行修改；可自主 commit / push；严格遵守 issue 流程（改动前建 issue、过程记录、小结关闭）。

## 五、代码约定

- Kotlin，包结构 `io.github.zmdld11.shuschedule`；UI 层 Compose，数据层 Room，`ui/` 不反向依赖 `data/` 之外的实现细节
- 解析逻辑（正方教务 JSON、周次文本）放纯 Kotlin 类，**必须带 JVM 单元测试**
- 涉及用户凭据的代码：只经系统 WebView/CookieManager，不落盘、不打日志
