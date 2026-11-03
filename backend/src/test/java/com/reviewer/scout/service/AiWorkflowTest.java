package com.reviewer.scout.service;

import com.reviewer.scout.controller.*;
import com.reviewer.scout.entity.Article;
import com.reviewer.scout.repository.ArticleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiWorkflowTest {
    @TempDir Path directory;
    final String owner = "a".repeat(64);
    ModelVault vault;
    ChatGateway gateway;
    ArticleRepository articles;
    AiController ai;
    @BeforeEach void setup() {
        vault = new ModelVault(directory); vault.save(owner, new ModelConnection(null, "Test", "deepseek", "https://api.deepseek.com", "deepseek-flash", "fake-test-key"));
        gateway = mock(ChatGateway.class); articles = mock(ArticleRepository.class); ai = new AiController(vault, gateway, articles, new TranslationCache(directory.toString()));
    }
    @Test void failedReplacementLeavesThePreviouslyWorkingConnectionUntouched() {
        ModelController controller = new ModelController(vault, gateway);
        String id = vault.active(owner).id();
        when(gateway.complete(any(), anyList(), anyInt())).thenThrow(new IllegalStateException("密钥无效"));
        assertThrows(IllegalStateException.class, () -> controller.save(owner, new ModelConnection(id, "Test", "deepseek", "https://api.deepseek.com", "deepseek-flash", "replacement-test-key")));
        assertEquals("fake-test-key", new ModelVault(directory).active(owner).apiKey());
    }
    @Test void missingAbstractDoesNotRequestInventedTranslationAndValidTranslationSurvivesReload() {
        Article article = new Article(); article.setId(1L); article.setTitle("Original title"); when(articles.findById(1L)).thenReturn(Optional.of(article));
        assertEquals(true, ai.translate(owner, 1L, new AiController.TranslationRequest("abstract")).get("missing"));
        when(gateway.complete(any(), anyList(), anyInt())).thenReturn("原始标题");
        assertEquals("原始标题", ai.translate(owner, 1L, new AiController.TranslationRequest("title")).get("text"));
        when(gateway.complete(any(), anyList(), anyInt())).thenThrow(new IllegalStateException("provider offline"));
        AiController restarted = new AiController(new ModelVault(directory), gateway, articles, new TranslationCache(directory.toString()));
        var cached = restarted.translate(owner, 1L, new AiController.TranslationRequest("title")); assertEquals("原始标题", cached.get("text")); assertEquals(true, cached.get("cached"));
    }
    @Test void assistantPreservesExplicitFiltersAndRejectsMalformedDateRanges() {
        when(gateway.complete(any(), anyList(), anyInt())).thenReturn("{\"ready\":true,\"reply\":\"草稿\",\"query\":\"HIV\",\"filters\":{\"dateFrom\":\"2026-05-03\",\"dateTo\":\"2026-11-03\"}}");
        var response = ai.assist(owner, new AiController.AssistantRequest(List.of(Map.of("role", "user", "content", "近半年")), null)); assertEquals("2026-05-03", response.path("filters").path("dateFrom").asText());
        when(gateway.complete(any(), anyList(), anyInt())).thenReturn("{\"ready\":true,\"reply\":\"草稿\",\"query\":\"HIV\",\"filters\":{\"dateFrom\":\"2026-11-03\",\"dateTo\":\"2026-05-03\"}}");
        assertThrows(IllegalStateException.class, () -> ai.assist(owner, new AiController.AssistantRequest(List.of(Map.of("role", "user", "content", "生成")), null)));
    }
    @Test void connectionTestReturnsAnUnsuccessfulDiagnosticInsteadOfABadGateway() {
        when(gateway.complete(any(), anyList(), anyInt())).thenThrow(new ModelConnectionException("authentication_failed", "密钥认证失败，请检查服务商与密钥是否匹配", 401));
        ModelController controller = new ModelController(vault, gateway);
        var result = assertDoesNotThrow(() -> controller.test(owner, new ModelConnection(vault.active(owner).id(), "Test", "deepseek", "https://api.deepseek.com", "deepseek-flash", "")));
        assertEquals(false, result.get("ok")); assertEquals("authentication_failed", result.get("code")); assertEquals(401, result.get("upstreamStatus"));
        assertEquals("fake-test-key", vault.active(owner).apiKey());
    }
}
