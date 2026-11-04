package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import javax.net.ssl.SSLException;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class ChatGateway {
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${models.analysis-timeout-seconds:300}")
    private int configuredAnalysisTimeoutSeconds = 300;
    public ChatGateway() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NEVER).build()); }
    ChatGateway(HttpClient client) { this.client = client; }
    public URI endpoint(ModelConnection c) {
        try {
            URI base = URI.create(c.baseUrl().strip().replaceAll("/+$", ""));
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null || (base.getPort() != -1 && base.getPort() != 443)) throw new IllegalArgumentException();
            if (base.getPath().toLowerCase().contains("/anthropic")) throw new IllegalArgumentException("检测到 Anthropic Messages 地址。此应用使用 Chat Completions 协议；智谱 Coding Plan 请使用 https://open.bigmodel.cn/api/coding/paas/v4");
            Map<String, String> hosts = Map.of("deepseek", "api.deepseek.com", "bigmodel", "open.bigmodel.cn", "bigmodel-coding", "open.bigmodel.cn", "zai", "api.z.ai", "zai-coding", "api.z.ai");
            if (hosts.containsKey(c.provider()) && !hosts.get(c.provider()).equalsIgnoreCase(base.getHost())) throw new IllegalArgumentException("地址与服务商不一致；其他服务请使用自定义连接");
            if (Set.of("bigmodel-coding", "zai-coding").contains(c.provider()) && !base.getPath().startsWith("/api/coding/paas/v4")) throw new IllegalArgumentException("已选择 Coding Plan，请填写对应的 /api/coding/paas/v4 地址；平台不会自动切换到通用 API");
            for (InetAddress ip : InetAddress.getAllByName(base.getHost())) if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress() || ip.isMulticastAddress() || (ip.getAddress()[0] & 0xff) == 0 || (ip.getAddress().length == 16 && (ip.getAddress()[0] & 0xfe) == 0xfc)) throw new IllegalArgumentException("请使用公网模型服务地址");
            return URI.create(base.toString() + (base.getPath().endsWith("/chat/completions") ? "" : "/chat/completions"));
        } catch (IllegalArgumentException e) { throw new IllegalArgumentException(e.getMessage() != null && (e.getMessage().contains("请") || e.getMessage().contains("服务商")) ? e.getMessage() : "请输入有效的 HTTPS 模型服务地址"); }
        catch (Exception e) { throw new IllegalArgumentException("模型服务域名无法解析，请检查 API 地址"); }
    }
    public String complete(ModelConnection c, List<Map<String, String>> messages, int maxTokens) {
        return complete(c, messages, maxTokens, Duration.ofSeconds(90), false);
    }
    public int analysisTimeoutSeconds() { return Math.max(120, Math.min(900, configuredAnalysisTimeoutSeconds)); }
    public String completeAnalysis(ModelConnection c, List<Map<String, String>> messages, int maxTokens) {
        return complete(c, messages, maxTokens, Duration.ofSeconds(analysisTimeoutSeconds()), true);
    }
    private String complete(ModelConnection c, List<Map<String, String>> messages, int maxTokens, Duration timeout, boolean analysis) {
        URI uri = endpoint(c);
        long started = System.nanoTime();
        int inputChars = messages.stream().mapToInt(m -> Objects.toString(m.get("content"), "").length()).sum();
        log.info("Model request: purpose={}, provider={}, model={}, inputChars={}, maxTokens={}, timeoutSeconds={}", analysis ? "evidence" : "chat", c.provider(), c.model(), inputChars, maxTokens, timeout.toSeconds());
        try {
            Map<String, Object> body = new LinkedHashMap<>(); body.put("model", c.model()); body.put("messages", messages); body.put("stream", false); body.put("max_tokens", maxTokens);
            if (Set.of("deepseek", "bigmodel", "zai", "bigmodel-coding", "zai-coding").contains(c.provider())) body.put("thinking", Map.of("type", "disabled"));
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type", "application/json").header("Authorization", "Bearer " + c.apiKey()).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw providerFailure(response.statusCode(), response.body(), c.provider());
            JsonNode choice = mapper.readTree(response.body()).path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) throw new ModelConnectionException("output_truncated", "模型输出被截断，请重试或调整模型", 200);
            String content = choice.path("message").path("content").asText("").trim();
            if (content.isBlank() || content.length() > 100000) throw new ModelConnectionException("invalid_response", "模型服务已响应，但未返回有效文本，请检查模型兼容性", 200);
            log.info("Model completed: provider={}, model={}, elapsedMs={}, outputChars={}", c.provider(), c.model(), (System.nanoTime()-started)/1_000_000, content.length());
            return content;
        } catch (HttpConnectTimeoutException e) {
            log.warn("Model connection timeout: provider={}, model={}, elapsedMs={}", c.provider(), c.model(), (System.nanoTime()-started)/1_000_000);
            throw new ModelConnectionException("network_error", "连接模型服务超时，尚未建立连接。请检查本机网络、代理和 API 地址后重试。" + (analysis ? "原有结果未被覆盖。" : ""), null);
        } catch (HttpTimeoutException e) {
            log.warn("Model timeout: purpose={}, provider={}, model={}, elapsedMs={}, inputChars={}, timeoutSeconds={}", analysis ? "evidence" : "chat", c.provider(), c.model(), (System.nanoTime()-started)/1_000_000, inputChars, timeout.toSeconds());
            throw new ModelConnectionException("timeout", analysis ? "本轮 AI 梳理等待模型超过 " + timeout.toSeconds() + " 秒，原有结果未被覆盖。可将本轮范围调为最多 20 篇重试；如仍超时，请检查服务商运行状态或切换模型。" : "等待模型响应超时，请稍后重试或切换模型", null);
        }
        catch (SSLException e) { throw new ModelConnectionException("tls_error", "模型服务的 TLS 连接或证书验证失败，请检查本机网络与证书配置", null); }
        catch (ConnectException e) { throw new ModelConnectionException("network_error", "本机后端无法连接模型服务，请检查网络和 API 地址", null); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ModelConnectionException("interrupted", "模型请求已中断，请重试", null); }
        catch (ModelConnectionException e) { throw e; }
        catch (Exception e) { log.warn("Model request failed: provider={}, model={}, failureType={}", c.provider(), c.model(), e.getClass().getSimpleName()); throw new ModelConnectionException("invalid_response", "模型服务连接或响应格式异常，请检查接口兼容性", null); }
    }

    private ModelConnectionException providerFailure(int status) {
        return switch (status) {
            case 400, 422 -> new ModelConnectionException("invalid_parameters", "模型服务拒绝了请求参数，请检查模型 ID 与 Chat Completions 接口兼容性", status);
            case 401 -> new ModelConnectionException("authentication_failed", "模型服务密钥认证失败。请确认密钥来自所选服务商的 API 平台，且未失效；不要填写网页登录密码或带 Bearer 前缀的文本", status);
            case 403 -> new ModelConnectionException("permission_denied", "模型服务拒绝访问，请检查账号权限、模型权限或服务区域限制", status);
            case 402 -> new ModelConnectionException("insufficient_balance", "模型服务账户余额或可用 API 额度不足，请在服务商平台检查账户状态", status);
            case 404 -> new ModelConnectionException("model_or_endpoint_not_found", "未找到模型或接口，请检查 API 地址和模型 ID", status);
            case 429 -> new ModelConnectionException("rate_limited", "模型服务限流或配额受限，请稍后重试并检查 API 配额", status);
            default -> new ModelConnectionException("upstream_unavailable", "模型服务暂时不可用，请稍后重试（上游 HTTP " + status + "）", status);
        };
    }

    private ModelConnectionException providerFailure(int status, String body, String provider) {
        if (!Set.of("bigmodel", "zai", "bigmodel-coding", "zai-coding").contains(provider)) return providerFailure(status);
        String code;
        try { code = mapper.readTree(body).path("error").path("code").asText(""); }
        catch (Exception ignored) { return providerFailure(status); }
        ModelConnectionException specific = switch (code) {
            case "1000", "1001", "1003", "1005" -> new ModelConnectionException("authentication_failed", "智谱 / Z.ai 认证失败或密钥已失效，请核对账号地区和对应平台的 API 密钥", status);
            case "1113" -> new ModelConnectionException("insufficient_balance", "模型账户余额不足或没有可用资源包，请核对所选端点、密钥类型和可用额度", status);
            case "1211" -> new ModelConnectionException("model_or_endpoint_not_found", "服务商未识别该模型 ID，请使用账号实际支持的模型名称", status);
            case "1311" -> new ModelConnectionException("plan_model_unavailable", "当前 Coding Plan 套餐没有所选模型的访问权限，请选择套餐支持的模型", status);
            case "1315" -> new ModelConnectionException("key_product_mismatch", "密钥类型与当前产品接口不匹配，请使用对应套餐或产品的 API 密钥", status);
            case "1309", "1314" -> new ModelConnectionException("plan_expired", "模型服务商提示套餐已到期，请在服务商控制台核查套餐状态", status);
            case "1308", "1310", "1316", "1317", "1318", "1319", "1320", "1321" -> new ModelConnectionException("plan_limit_exceeded", "模型套餐的当前用量窗口或预算限制已达到，请在服务商控制台查看恢复时间与额度", status);
            case "1313" -> new ModelConnectionException("plan_policy_limited", "服务商按套餐使用规则限制了请求，请在服务商控制台核查限制原因", status);
            default -> null;
        };
        return specific == null ? providerFailure(status) : new ModelConnectionException(specific.getCode(), specific.getMessage(), status, code);
    }
}
