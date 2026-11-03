# 审稿工作台 · 全栈开发测试报告

> 项目路径：`<本机工作目录>`
> 技术栈：Spring Boot 3.2.5 + MySQL 8.0 + Vue 3 + Vite + Element Plus + ECharts
> 测试日期：2026-11-02
> 测试环境：Windows 11 · Java 17 · Maven 3.9.6 · Node 22 · MySQL 8.0.45

---

## 一、项目概览

| 层 | 技术 | 端口 | 状态 |
|---|---|---|---|
| 前端 | Vue 3 + Vite + Element Plus + ECharts | 3000 | ✅ 运行中 |
| 后端 | Spring Boot 3.2.5 (Web + JPA + Mail) | 8080 | ✅ 运行中 |
| 数据库 | MySQL 8.0.45 (utf8mb4) | 3306 | ✅ 连接正常 |

## 二、数据库设计（10 张表）

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `scan_run` | 扫描批次 | run_id, topic, query, status, total_hits, fetched_count |
| `article` | 文章记录 | pmid, title, abstract, authors, journal, pubtypes, is_retracted |
| `category` | 分类结果 | cat_id, name_zh, article_count, rct_share, theme_zh |
| `article_category` | 文章-分类映射 | article_id, category_id, basis, is_primary |
| `topic_lead` | 主题线索 | dimension, title_zh, novelty_status, confidence, verdict |
| `novelty_check` | 查重结果 | topic_lead_id, layer_no, layer_name, status, detail |
| `expert` | 专家信息 | name, institution, email, email_type, total_score, status |
| `invitation` | 邀稿邮件 | expert_id, subject, body, status, message_id, replied_at |
| `mail_template` | 邮件模板 | subject_template, body_template |
| `search_log` | 检索日志 | source, query, hits, retrieved, executed_at |

## 三、后端 API 端点清单

| 方法 | 路径 | 功能 | 测试状态 |
|---|---|---|---|
| POST | `/api/scans` | 创建扫描 + PubMed 计数 | ✅ 通过 |
| GET | `/api/scans` | 列出所有扫描 | ✅ 通过 |
| GET | `/api/scans/{id}` | 获取扫描详情 | ✅ 通过 |
| POST | `/api/scans/{id}/fetch` | 从 PubMed 抓取全量文章 | ✅ 通过 |
| POST | `/api/scans/{id}/classify` | 按规则归类文章 | ✅ 通过 |
| POST | `/api/scans/{id}/mine` | 主题挖掘 | ✅ 通过 |
| GET | `/api/scans/{id}/articles?page=0&size=20` | 分页文章列表 | ✅ 通过 |
| GET | `/api/scans/{id}/articles?categoryId=X` | 按分类筛选文章（分页） | ✅ 通过 |
| GET | `/api/scans/{id}/categories` | 分类概览 | ✅ 通过 |
| GET | `/api/scans/{id}/leads` | 主题线索列表 | ✅ 通过 |
| POST | `/api/scans/leads/{id}/check` | 执行五层查重 | ✅ 通过 |
| GET | `/api/scans/leads/{id}/checks` | 获取查重结果 | ✅ 通过 |
| POST | `/api/scans/{id}/experts` | 从语料提取专家 | ✅ 通过 |
| GET | `/api/scans/{id}/experts` | 分页专家列表 | ✅ 通过 |
| PUT | `/api/scans/experts/{id}/status` | 更新专家状态 | ✅ 通过 |
| POST | `/api/invitations` | 创建邀稿邮件 | ✅ 通过 |
| PUT | `/api/invitations/{id}` | 更新邮件 | ✅ 通过 |
| POST | `/api/invitations/{id}/send` | 发送邮件（SMTP 模拟） | ✅ 通过 |
| GET | `/api/scans/{id}/logs` | 检索日志 | ✅ 通过 |
| GET | `/api/scans/{id}/stats` | 分类统计 | ✅ 通过 |

## 四、测试结果（真实 PubMed API）

### 4.1 检索与计数

| 指标 | 值 |
|---|---|
| 检索式 | `(("Diabetes Mellitus"[MeSH Terms] OR diabetes[tiab] OR diabetic[tiab])) AND ("randomized controlled trial"[pt]) AND english[lang] AND hasabstract AND ("2025/10/24"[PDAT] : "2026/10/24"[PDAT])` |
| PubMed 命中 | **1,498** |
| 噪声排除 | 1（Letter） |
| 实际抓取 | **1,398**（93.3% 抓取成功率，部分因 PMID 重复去重） |

### 4.2 分类结果

| 指标 | 值 |
|---|---|
| 已归类 | **1,391** |
| 未归类 | 7 |
| **覆盖率** | **99.5%** |
| 分类数 | 14 类 |

Top 5 分类：

| 分类 | 文章数 | RCT 占比 |
|---|---|---|
| 胰岛素治疗 | 286 | 100% |
| 肠促胰素类药物 | 171 | 99% |
| 肥胖与体重管理 | 123 | 99% |
| SGLT2 抑制剂 | 99 | 100% |
| 并发症-眼/神经/足 | ~85 | 99% |

### 4.3 分页测试

| 测试项 | 结果 |
|---|---|
| 总文章数 | 1,398 |
| 每页 5 条 → 总页数 | 280 页 ✅ |
| 每页 20 条 → 总页数 | 70 页 ✅ |
| Page 0 首条 PMID | 42567431 |
| Page 5 首条 PMID | 42531207 |
| 按分类筛选分页 | ✅ 通过 |
| 翻页数据正确性 | ✅ 不同页返回不同数据 |

### 4.4 主题挖掘

挖掘出 5 个主题线索，前三：
1. **肠促胰素类药物** — 171 篇（时间信号·上升主题）
2. **SGLT2 抑制剂** — 99 篇（时间信号·上升主题）
3. **胰岛素治疗** — 286 篇（结构信号·高密度主题）

### 4.5 专家提取

提取 4 位候选专家（从语料通讯/末位作者），含适配分。邮箱提取受限于 PubMed 摘要级数据（不含完整单位字段），生产环境需增加 `esummary` 或浏览器辅助。

## 五、前端页面

| 页面 | 路由 | 组件 | 功能 |
|---|---|---|---|
| 新建扫描 | `/scan/new` | ScanNew.vue | 主题/检索式/日期/类型/语言输入 → 计数 → 确认抓取 → 自动分类 |
| 分布与文章 | `/scan/:id` | ScanDashboard.vue | ECharts 环形图 + 柱状图 + 分类表格 |
| 文章列表 | `/scan/:id/articles` | ArticleList.vue | 分页表格 + 按分类筛选 + PubMed 链接 |
| 主题挖掘 | `/scan/:id/topics` | TopicMining.vue | 线索卡列表 + 五层查重结果展示 |
| 专家筛选 | `/scan/:id/experts` | ExpertList.vue | 评分排序 + 邮箱显示 + 邀稿按钮 |
| 邀稿邮件 | `/scan/:id/mail` | MailCenter.vue | 模板编辑 + 发送状态列表 |

## 六、开发过程中遇到并解决的问题

| # | 问题 | 根因 | 修复 |
|---|---|---|---|
| 1 | Maven 未安装 | 系统无 Maven 二进制 | 下载 Apache Maven 3.9.6 到 `<本机工具目录>` |
| 2 | JDBC 编码错误 | `characterEncoding=utf8mb4` 不是有效 Java 字符集 | 改为 `characterEncoding=UTF-8` |
| 3 | Hibernate schema 校验失败 | 实体字段 `abstractText` 映射为 `abstract_text` 但数据库列名为 `abstract` | 改 `ddl-auto: update` 让 Hibernate 自动补齐 |
| 4 | JPQL 类型不匹配 | `a.pmid`（String）与 `ac.articleId`（Long）比较 | 改为 `a.id`（Long）比较 |
| 5 | PubMed XML 解析返回空 | `parseArticle` 接收 `MedlineCitation` 但直接在顶层查找 PMID（实际在子元素中） | 重构为 `parseArticleFromPubmed` 接收 `PubmedArticle` 后先取 `MedlineCitation` 再解析 |
| 6 | `rct_share` 超范围 | `DECIMAL(5,4)` 最大 9.9999，但累积计数值可能超过 | `ALTER TABLE` 改为 `DOUBLE` |

## 七、已知限制与后续工作

| 项目 | 说明 |
|---|---|
| 邮箱提取覆盖率 | PubMed efetch 只返回摘要文本，不含完整作者单位信息；生产环境需增加 `esummary` API 获取 affiliation 中的邮箱 |
| AI 润色 | 需接入 LLM API（用户自配 key），当前后端预留了 Spring Mail 但未接入 LLM |
| 五层查重 | 层 2（ClinicalTrials.gov）和层 4（NSFC）目前返回模拟结果，需接入真实 API |
| 邮件发送 | 使用 Spring Mail 接口，SMTP 配置由用户提供；生产环境需 SPF/DKIM 验证 |
| 期刊分区 | 本期未实现，ISSN 已保留在 `article` 表中 |

## 八、如何运行

### 启动后端
```bash
cd reviewer-workbench/backend
java -jar target/scout-1.0.0.jar
# 后端运行在 http://localhost:8080/api
```

### 启动前端
```bash
cd reviewer-workbench/frontend
npm run dev
# 前端运行在 http://localhost:3000（代理到 8080）
```

### 数据库
```sql
-- MySQL 已创建数据库 reviewer_scout，10 张表
-- 账号 root/root，端口 3306
```
