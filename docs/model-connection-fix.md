# 模型连接诊断与 Coding Plan 修复

## 变更

- 移除根据 Coding Plan 用途说明进行的本地端点拦截。新增国内智谱和国际 Z.ai Coding Plan 预设，保存、测试及模型调用均保留配置中的专用地址。
- 不模拟其他客户端身份，不绕过服务商权限，不自动改用通用 API 或其他计费渠道。服务商套餐规则仍适用。
- 独立连接测试用 HTTP 200 返回 `ok: false` 及安全诊断；真正成功才返回 `ok: true`。失败不会保存或覆盖密钥。
- 模型调用失败包含稳定分类、上游 HTTP 状态和已知 GLM 业务码；不回传原始响应体，避免上游回显密钥。
- 区分认证、额度、参数、模型权限、套餐类型、套餐用量、服务商规则限制、限流、TLS 和网络问题。
- 保存接口在测试失败时仍返回非成功状态；前端同时检查 HTTP 与 `ok`，避免将失败诊断误显示为“连接成功”。
- 测试输出预算调为最多 1024 tokens，兼容仍有轻量思考的模型；输出被截断仍不会当作完整成功。

## 证据与限制

已用失败测试复现端点被本地拒绝、Coding Plan 无法持久化、所有连接失败成为通用异常和 `ok:false` 被前端当成功的问题，再实现修复。使用无效诊断密钥仅验证请求能到达服务商、返回可区分的认证失败；没有读取或使用用户的真实密钥，不能据此声称用户账号已经连通。

DeepSeek 的 `deepseek-flash` 模型名来自[官方文档](https://api-docs.deepseek.com/)。智谱 Coding Plan 的地址和模型名参考[模型切换说明](https://docs.bigmodel.cn/cn/coding-plan/latest-model)；密钥产品归属参考[API Key 分类管理](https://docs.bigmodel.cn/cn/coding-plan/notice/api-key-management)；错误分类参考[官方错误码](https://docs.z.ai/api-reference/api-code)。
