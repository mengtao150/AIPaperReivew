package com.reviewer.scout.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelVaultTest {
    @TempDir Path directory;
    final String owner = "a".repeat(64), other = "b".repeat(64);
    ModelConnection draft(String id, String key) { return new ModelConnection(id, "我的 DeepSeek", "deepseek", "https://api.deepseek.com", "deepseek-flash", key); }

    @Test void credentialsPersistEncryptedAndPublicMetadataNeverReturnsThem() throws Exception {
        ModelVault vault = new ModelVault(directory);
        vault.save(owner, draft(null, "test-private-key-12345"));
        String content = Files.readString(directory.resolve(owner + ".json"));
        assertFalse(content.contains("test-private-key-12345"));
        assertFalse(vault.list(owner).toString().contains("test-private-key-12345"));
        assertEquals("test-private-key-12345", new ModelVault(directory).active(owner).apiKey());
    }
    @Test void blankEditPreservesKeyAndDifferentBrowserCannotReadOrModifyIt() {
        ModelVault vault = new ModelVault(directory);
        vault.save(owner, draft(null, "test-private-key-12345"));
        String id = vault.active(owner).id();
        vault.save(owner, draft(id, ""));
        assertEquals("test-private-key-12345", vault.active(owner).apiKey());
        assertThrows(IllegalArgumentException.class, () -> vault.resolve(other, draft(id, "")));
        assertThrows(IllegalArgumentException.class, () -> vault.activate(other, id));
        vault.delete(owner, id);
        assertThrows(IllegalArgumentException.class, () -> vault.active(owner));
    }
    @Test void pathTraversalCannotAddressAnotherSettingsFile() {
        assertThrows(IllegalArgumentException.class, () -> new ModelVault(directory).list("../../escape"));
    }
    @Test void codingPlanConnectionSurvivesRestartWithoutEndpointRewriting() {
        ModelVault vault = new ModelVault(directory);
        vault.save(owner, new ModelConnection(null, "GLM Coding", "bigmodel-coding", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-4.7", "fake-test-key"));
        ModelConnection restored = new ModelVault(directory).active(owner);
        assertEquals("bigmodel-coding", restored.provider()); assertEquals("https://open.bigmodel.cn/api/coding/paas/v4", restored.baseUrl());
    }
}
