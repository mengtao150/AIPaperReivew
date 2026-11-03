package com.reviewer.scout.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class TranslationCache {
    private final Path directory;
    public TranslationCache(@Value("${scout.models-directory:${user.home}/.reviewer-scout/models}") String directory) { this.directory = Path.of(directory).resolve("translations"); }
    public String key(String workspace, ModelConnection c, String source) {
        if (!workspace.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("工作区标识无效");
        try { return workspace + "-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((c.baseUrl() + "\n" + c.model() + "\nzh-v1\n" + source).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("翻译缓存不可用"); }
    }
    public String get(String key) { try { Path file = directory.resolve(key + ".txt"); return Files.exists(file) ? Files.readString(file) : null; } catch (Exception e) { return null; } }
    public void put(String key, String text) {
        try { Files.createDirectories(directory); Path temp = Files.createTempFile(directory, "translation-", ".tmp"); try { Files.writeString(temp, text); Files.move(temp, directory.resolve(key + ".txt"), StandardCopyOption.REPLACE_EXISTING); } finally { Files.deleteIfExists(temp); } }
        catch (Exception ignored) { /* A failed cache write must not discard a valid translation. */ }
    }
}
