package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatGatewayTest {
    ModelConnection connection(String url) { return new ModelConnection(null, "Test", "custom", url, "test-model", "fake-test-key"); }
    @Test void analysisHasItsOwnLongRequestBudgetWhileOrdinaryCallsKeepTheirExistingLimit() throws Exception {
        HttpClient client=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"ok\"}}]}");
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        ChatGateway gateway=new ChatGateway(client);
        gateway.complete(connection("https://8.8.8.8"),List.of(),1024);
        gateway.completeAnalysis(connection("https://8.8.8.8"),List.of(),8000);
        var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client,times(2)).send(requests.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals(Duration.ofSeconds(90),requests.getAllValues().get(0).timeout().orElseThrow());
        assertEquals(Duration.ofSeconds(300),requests.getAllValues().get(1).timeout().orElseThrow());
    }
    @Test void analysisTimeoutReportsTheAppliedBudgetAndPreservesItsErrorCode() throws Exception {
        HttpClient client=mock(HttpClient.class);when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("simulated slow response"));
        var error=assertThrows(ModelConnectionException.class,()->new ChatGateway(client).completeAnalysis(connection("https://8.8.8.8"),List.of(),8000));
        assertEquals("timeout",error.getCode());assertTrue(error.getMessage().contains("300"));assertFalse(error.getMessage().contains("fake-test-key"));
    }
    @Test void connectionTimeoutIsNotMisreportedAsExhaustingTheAnalysisDeadline() throws Exception {
        HttpClient client=mock(HttpClient.class);when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpConnectTimeoutException("simulated connection timeout"));
        var error=assertThrows(ModelConnectionException.class,()->new ChatGateway(client).completeAnalysis(connection("https://8.8.8.8"),List.of(),8000));
        assertEquals("network_error",error.getCode());assertTrue(error.getMessage().contains("建立连接"));assertFalse(error.getMessage().contains("300"));
    }
    @Test void unsafeLocalEndpointsAreRejectedBeforeCredentialsAreSent() {
        ChatGateway gateway = new ChatGateway();
        for (String url : List.of("http://example.com/v1", "https://127.0.0.1", "https://169.254.169.254", "https://[::1]", "https://user:pass@example.com")) assertThrows(IllegalArgumentException.class, () -> gateway.endpoint(connection(url)));
    }
    @Test void codingPlanChatEndpointsArePreservedWithoutSwitchingToGeneralBilling() {
        ChatGateway gateway = new ChatGateway();
        var domestic = new ModelConnection(null, "Coding", "bigmodel-coding", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-4.7", "fake-test-key");
        var global = new ModelConnection(null, "Coding", "zai-coding", "https://api.z.ai/api/coding/paas/v4/chat/completions", "glm-4.7", "fake-test-key");
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4/chat/completions", gateway.endpoint(domestic).toString());
        assertEquals("https://api.z.ai/api/coding/paas/v4/chat/completions", gateway.endpoint(global).toString());
    }
    @Test void successfulCompletionReadsContentAndNeverSerializesCredential() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"中文标题\"}}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertEquals("中文标题", new ChatGateway(client).complete(connection("https://8.8.8.8/v1"), List.of(Map.of("role", "user", "content", "translate")), 500));
        assertFalse(new ObjectMapper().writeValueAsString(connection("https://8.8.8.8/v1")).contains("fake-test-key"));
        assertFalse(connection("https://8.8.8.8/v1").toString().contains("fake-test-key"));
    }
    @Test void upstreamErrorsAreSanitizedAndTruncatedTranslationsAreRejected() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(401); when(response.body()).thenReturn("error: fake-test-key");
        Exception error = assertThrows(IllegalStateException.class, () -> new ChatGateway(client).complete(connection("https://8.8.8.8"), List.of(), 100)); assertFalse(error.getMessage().contains("fake-test-key"));
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}");
        assertThrows(IllegalStateException.class, () -> new ChatGateway(client).complete(connection("https://8.8.8.8"), List.of(), 100));
    }
    @Test void providerAuthenticationAndBalanceErrorsRemainDistinguishable() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.body()).thenReturn("{\"error\":{\"message\":\"fake-test-key\"}}");
        when(response.statusCode()).thenReturn(401);
        var authentication = assertInstanceOf(ModelConnectionException.class, assertThrows(IllegalStateException.class, () -> new ChatGateway(client).complete(connection("https://8.8.8.8"), List.of(), 128)));
        assertEquals("authentication_failed", authentication.getCode()); assertEquals(401, authentication.getUpstreamStatus());
        when(response.statusCode()).thenReturn(402);
        var balance = assertInstanceOf(ModelConnectionException.class, assertThrows(IllegalStateException.class, () -> new ChatGateway(client).complete(connection("https://8.8.8.8"), List.of(), 128)));
        assertEquals("insufficient_balance", balance.getCode()); assertFalse(balance.getMessage().contains("fake-test-key"));
    }
    @Test void glmBalanceErrorIsNotMisdiagnosedAsTemporaryRateLimiting() throws Exception {
        HttpClient client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(429); when(response.body()).thenReturn("{\"error\":{\"code\":\"1113\",\"message\":\"fake-test-key\"}}");
        var glm = new ModelConnection(null, "GLM", "bigmodel-coding", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5.3-flash", "fake-test-key");
        var error = assertInstanceOf(ModelConnectionException.class, assertThrows(IllegalStateException.class, () -> new ChatGateway(client).complete(glm, List.of(), 128)));
        assertEquals("insufficient_balance", error.getCode()); assertFalse(error.getMessage().contains("fake-test-key"));
    }
}
