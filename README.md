# AIPaperReivew · 审稿人工作台

Vue 3 + Spring Boot 的本机研究工作空间。面向文献检索、原文与中文阅读、用户自带密钥模型连接的研究辅助项目。当前处于开发阶段。

## 启动

需要 Java 17、Maven 3.9+、Node.js 22（含 npm）和 MySQL 8。以下命令以 PowerShell 为例。

```powershell
git clone https://github.com/mengtao150/AIPaperReivew.git
cd AIPaperReivew
```

用数据库管理员账号执行以下 SQL，替换示例密码，创建专用数据库和本机应用用户：

```sql
CREATE DATABASE reviewer_scout CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'reviewer_scout'@'localhost' IDENTIFIED BY 'replace-with-your-own-password';
GRANT ALL PRIVILEGES ON reviewer_scout.* TO 'reviewer_scout'@'localhost';
```

在启动后端的终端中设置环境变量（不会自动读取 `.env` 文件）：

```powershell
$env:SPRING_DATASOURCE_USERNAME = 'reviewer_scout'
$env:SPRING_DATASOURCE_PASSWORD = '<填写刚设置的数据库密码>'
$env:PUBMED_EMAIL = '<填写联系邮箱>'
# 可选：数据库不在默认地址时设置完整 JDBC URL
# $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/reviewer_scout?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false'
```

配置入口为 `backend/src/main/resources/application.yml`。数据库密码必须显式提供；首次启动由 Hibernate `ddl-auto: update` 创建/更新表，无需导入个人数据库。该方式仅用于开发，正式部署需版本化迁移与备份。默认 JDBC 连接关闭 TLS，仅适用于本机开发。

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:3000`。若 8080 已由其他程序占用，可使用独立端口，避免停止未知服务：

打包命令为 `mvn package`，生成 `backend/target/reviewer-workbench.jar`，可用 `java -jar target/reviewer-workbench.jar --server.port=8081` 启动。

```powershell
cd backend
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
```

```powershell
cd frontend
$env:SCOUT_BACKEND_URL = 'http://localhost:8081'
npm run dev
```

## 模型配置

打开“模型设置”，选择服务商，填写连接名称、通用 API 地址、模型 ID 和自己的密钥，点击“测试并保存”。这是实际的简短模型调用，只有成功获得有效文本才保存。支持多个连接、默认连接切换、独立连接测试、密钥替换和连接删除。编辑时密钥留空会保留原密钥；修改失败不会覆盖原配置。

- DeepSeek 默认通用地址 `https://api.deepseek.com`。
- 智谱 GLM 默认通用地址 `https://open.bigmodel.cn/api/paas/v4`。
- Z.ai 默认通用地址 `https://api.z.ai/api/paas/v4`。
- 智谱 GLM Coding Plan 默认专用地址 `https://open.bigmodel.cn/api/coding/paas/v4`；Z.ai Coding Plan 为 `https://api.z.ai/api/coding/paas/v4`。
- 支持自定义公网 HTTPS Chat Completions 兼容服务，禁止私网、环回、带凭证的 URL 和重定向。
- Coding Plan 地址可以独立配置和测试，不会被本地用途规则拦截，也不会自动改为通用地址或回退到另一种计费额度。服务商对套餐用途、模型和账号权限的要求仍适用，真实可用性以服务商响应为准。
- “测试连接”完成后返回实际诊断结果；认证失败、余额不足、模型/参数不兼容、限流、超时分别显示，不统一包装为 502。测试失败不会被标成成功，也不会保存或覆盖已有密钥。
- 模型 ID 可编辑，默认值不保证用户账号已开通对应权限。

服务商文档：[DeepSeek](https://api-docs.deepseek.com/)、[ZCode 模型连接说明](https://zcode.z.ai/cn/docs/configuration)。

## 保存与隔离

密钥由服务端使用 AES-256-GCM 加密，保存在 `${user.home}/.reviewer-scout/models`，不会回显到 API、日志或浏览器存储。可通过 `SCOUT_MODELS_DIRECTORY` 覆盖存储目录。该目录包含加密配置、`master.key` 及公开文献的翻译缓存；备份和迁移应保护整个目录，丢失加密主密钥后需要重新配置服务密钥。

当前浏览器由随机 HttpOnly、SameSite=Strict Cookie 识别工作区。配置跨后端重启保留；清除站点 Cookie、更换浏览器或设备，需要重新建立连接。翻译按工作区、服务地址、模型和原文缓存，避免重复悬浮产生重复调用。未配置模型时，原文检索和阅读仍可使用。

当前应用尚未提供账户登录或完整租户隔离，文献记录仍沿用原有共享数据库；此 Cookie 不是账户认证。商业化部署前需补齐账户权限、租户归属、CSRF/限流、密钥管理系统、审计、正式数据库迁移、HTTPS 和作业持久化。不要直接将本机开发服务公开到互联网。

## 检索行为

直接输入 PubMed 原生语法，或由 AI 对话生成可编辑草稿。明确提出的日期、类型和语言条件单独展示并应用，AI 不自动启动检索。所有筛选默认不限，最终条件由后端统一编译；修改条件后旧命中数立即失效。试检索显示实际命中数、PubMed 查询解释与警告。当前每轮最多获取前 10,000 条，界面会明确提示，不能将此上限称为完整系统综述检索。

原标题和结构化摘要保留；标题、摘要悬浮或键盘聚焦时按需翻译，长内容可在阅读抽屉对照查看。机器翻译明确标注，缺失摘要不会被模型补写。

文献列表和阅读详情展示“作者 / 专家邮箱”。仅保存原始作者单位信息和摘要中实际出现的地址，并附来源与原文片段；共享地址合并关联作者，摘要中无法确定归属的地址标记“归属待核实”，不会推断通讯作者。没有记录统一显示“未找到”。新获取的文献自动保存这些信息；旧文献可点击“补查本页邮箱”，最多一次补查当前页 100 篇的 PubMed 元数据，结果持久保存。这里的“未找到”仅表示当前已获取来源未提供可核实邮箱，并不代表已穷尽全网。

## 验证

```powershell
cd backend
mvn test
# 可选：直接访问官方 PubMed，验证一条公开记录的查询解释、标题、摘要和 DOI
$env:SCOUT_LIVE_PUBMED = 'true'
mvn -Dtest=PubMedLiveTest test
```

```powershell
cd frontend
npm test
npm run build
npx playwright install chromium
# 先运行本机前后端；浏览器测试用受控服务响应，不需要任何真实模型密钥
npm run test:e2e
```

可用 `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` 指定本机 Chromium 可执行文件。`SCOUT_TEST_URL` 可覆盖浏览器测试地址。真实 DeepSeek/GLM 的模型可用性、费用与密钥权限需使用本人账号在设置页测试；仓库不内置任何可用密钥。


## 项目结构

```text
backend/       Spring Boot REST API、JPA 实体与后端测试
frontend/      Vue 3、Vite、Element Plus、ECharts 与浏览器测试
docs/          实现说明和开发计划
```

前端开发端口为 3000，后端为 8080，API 前缀为 `/api`。Vite 开发代理由 `SCOUT_BACKEND_URL` 指定；`npm run build` 输出 `frontend/dist`，生产托管需自行配置静态资源与 `/api` 反向代理。

## 功能状态与使用边界

- PubMed 检索、原文元数据阅读、按需翻译、模型连接管理及来源邮箱提取已提供实现与测试。
- 规则分类、主题线索和专家工作流仍需人工核查，不代表已完成系统综述、研究查新或学术评审。
- 新增“领域梳理”工作台：进入已有任务后从侧栏打开，包含领域图谱、综述覆盖矩阵、来源关联的梳理笔记和候选子议题。基础视图不需要模型；一篇文献可关联多个方向。连线表示共同文献，时间图使用实际发表月份，缺少月份的记录单独报告。
- 综述按 PubMed 文章类型识别，可记录检索截止日期、覆盖内容、局限和人工核对状态。矩阵区分“尚未核实”“摘要涉及”和编辑核对全文后的深入/部分/未覆盖；摘要匹配不会自动变成全文覆盖或研究空白结论。
- “AI 梳理与提炼”仅在点击并确认后调用当前模型。一次选择最多20/40/80篇，优先纳入综述并对其余文献按检索顺序等距取样；每篇摘要最多2200字符，总标题摘要最多70000字符，实际纳入的 PMID、模型和时间保存并可导出。这是初步编辑梳理，不能视为完整领域证据综合。
- AI 草稿中的文献与引句必须来自本轮实际提交材料，且属于对应主题；校验引用存在不代表判断已获科学验证。模型、格式或引文校验失败保留原结果。重新分析会替换主题、笔记和子议题，界面明确提醒先导出需要保留的内容。
- AI 引文采用原文片段编号：服务端把实际提交的标题/摘要切为带编号的连续片段，模型选择 `evidenceId`，服务端回填原始 PMID、字段和引句后再严格校验。摘要片段最多350字符，不用模型重新抄写原文。未知编号、冲突的来源信息、伪造引句仍会拒绝；校验错误返回具体规则和字段位置，日志不记录原文或完整模型回答。编号只保证来源可追溯，判断是否得到上下文支持仍需编辑核对。
- 笔记包含共识线索、持续争议、空白/薄弱环节与背景，支持逐条编辑、确认和原文抽屉核对。子议题卡记录核心问题、邀稿依据、差异化假设、边界、提纲与编辑决策。支持导出 Markdown 笔记。相关作者由本方向文献生成，只有原文明确关联的邮箱才展示，身份和邀稿适配性仍待核实。
- 梳理结果保存在新表 `evidence_document`，按浏览器工作区与检索任务分别保存；并发编辑通过版本号返回409冲突提示，文献库变化时显示结果过期提示。当前仅保留最新版本，不提供历史版本恢复。AI 作业为进程内异步作业，服务重启会中断未完成分析，已保存版本保留。旧版占位主题挖掘和“五层查重”写入接口已停用。
- AI 梳理使用独立的模型请求超时，默认300秒，可用 `SCOUT_ANALYSIS_TIMEOUT_SECONDS` 调整（实际限制120–900秒）；普通模型调用仍为90秒。梳理输出控制在最多6个主题、每主题3条笔记、3个候选选题，总输出上限8000 tokens。后台返回开始时间、实际提交篇数和请求期限，页面显示真实已等待时间。超时保留原结果，可选择最多20篇重试；平台不自动重试产生额外调用。服务日志仅记录服务商、模型、字符数、耗时、请求期限等诊断元数据，不记录密钥、输入原文或完整模型输出。
- 邮件中心的发送接口当前为模拟状态更新，不会实际通过 SMTP 发送邮件；界面出现 SENT 不能视为真实投递。
- 模型调用将相关输入发送到用户选择的服务商，可能产生费用。请遵守服务商条款、文献版权及个人信息使用要求，勿提交未授权的保密稿件。
- 本项目用于研究辅助，不提供医疗诊断或治疗建议。AI 输出、翻译和联系方式归属均需核验。

## 参与贡献与安全反馈

提交问题前请先搜索 [Issues](https://github.com/mengtao150/AIPaperReivew/issues)。贡献代码请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，安全问题请参见 [SECURITY.md](SECURITY.md)。请勿在 Issue、PR、日志或截图中上传密钥、数据库密码、私人文献或个人数据。

`REPORT.md` 为历史开发记录，其结果不代表当前版本的验证结论；请以本地执行测试及 CI 结果为准。

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。第三方依赖和外部文献保留其各自的许可与权利。
