package com.reviewer.scout.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class ModelVault {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    @Autowired public ModelVault(@Value("${scout.models-directory:${user.home}/.reviewer-scout/models}") String directory) { this(Path.of(directory)); }
    public ModelVault(Path directory) { this.directory = directory; }
    public synchronized Map<String, Object> list(String workspace) {
        Data data = read(workspace);
        return Map.of("profiles", data.profiles.stream().map(this::publicView).toList(), "activeId", data.activeId == null ? "" : data.activeId);
    }
    public synchronized ModelConnection resolve(String workspace, ModelConnection draft) {
        if (draft == null) return active(workspace);
        String key = draft.apiKey();
        if (draft.id() != null && !draft.id().isBlank()) {
            Stored old = find(read(workspace), draft.id());
            if (key == null || key.isBlank()) key = decrypt(old.encryptedKey);
        }
        if (key == null || key.isBlank() || key.length() > 4096 || key.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("请输入有效的 API 密钥");
        if (draft.name() == null || draft.name().isBlank() || draft.name().length() > 80) throw new IllegalArgumentException("请填写连接名称（最多 80 字符）");
        if (!Set.of("deepseek", "bigmodel", "zai", "bigmodel-coding", "zai-coding", "custom").contains(draft.provider() == null ? "" : draft.provider())) throw new IllegalArgumentException("请选择模型服务商");
        if (draft.model() == null || !draft.model().matches("[a-zA-Z0-9._:/-]{1,150}")) throw new IllegalArgumentException("模型 ID 格式不正确");
        return new ModelConnection(draft.id(), draft.name().trim(), draft.provider(), draft.baseUrl(), draft.model(), key.trim());
    }
    public synchronized Map<String, Object> save(String workspace, ModelConnection draft) {
        ModelConnection c = resolve(workspace, draft); Data data = read(workspace);
        Stored p = c.id() == null || c.id().isBlank() ? new Stored() : find(data, c.id());
        if (p.id == null) { if (data.profiles.size() >= 12) throw new IllegalArgumentException("最多保存 12 个模型连接"); p.id = UUID.randomUUID().toString(); data.profiles.add(p); }
        p.name = c.name(); p.provider = c.provider(); p.baseUrl = c.baseUrl(); p.model = c.model(); p.encryptedKey = encrypt(c.apiKey()); p.testedAt = Instant.now().toString();
        if (data.activeId == null) data.activeId = p.id;
        write(workspace, data); return publicView(p);
    }
    public synchronized ModelConnection active(String workspace) {
        Data data = read(workspace);
        if (data.activeId == null) throw new IllegalArgumentException("请先在模型设置中测试并保存一个连接");
        Stored p = find(data, data.activeId); return new ModelConnection(p.id, p.name, p.provider, p.baseUrl, p.model, decrypt(p.encryptedKey));
    }
    public synchronized void activate(String workspace, String id) { Data data = read(workspace); find(data, id); data.activeId = id; write(workspace, data); }
    public synchronized void delete(String workspace, String id) { Data data = read(workspace); find(data, id); data.profiles.removeIf(p -> p.id.equals(id)); if (id.equals(data.activeId)) data.activeId = data.profiles.isEmpty() ? null : data.profiles.get(0).id; write(workspace, data); }
    private Map<String, Object> publicView(Stored p) { return Map.of("id", p.id, "name", p.name, "provider", p.provider, "baseUrl", p.baseUrl, "model", p.model, "hasKey", true, "maskedKey", "••••••••••••", "testedAt", p.testedAt); }
    private Stored find(Data data, String id) { return data.profiles.stream().filter(p -> p.id.equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("模型连接不存在，请刷新设置")); }
    private Path file(String workspace) { if (workspace == null || !workspace.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("工作区标识无效"); return directory.resolve(workspace + ".json"); }
    private Data read(String workspace) {
        Path path = file(workspace); try { return Files.exists(path) ? mapper.readValue(path.toFile(), Data.class) : new Data(); }
        catch (Exception e) { throw new IllegalStateException("无法读取模型设置，请检查本机存储权限"); }
    }
    private void write(String workspace, Data data) {
        try { Files.createDirectories(directory); Path temp = Files.createTempFile(directory, "profile-", ".tmp"); try { mapper.writeValue(temp.toFile(), data); try { Files.move(temp, file(workspace), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (AtomicMoveNotSupportedException e) { Files.move(temp, file(workspace), StandardCopyOption.REPLACE_EXISTING); } } finally { Files.deleteIfExists(temp); } }
        catch (Exception e) { throw new IllegalStateException("模型设置保存失败，请检查本机存储权限"); }
    }
    private byte[] masterKey() throws Exception {
        Files.createDirectories(directory); Path path = directory.resolve("master.key");
        if (!Files.exists(path)) { byte[] bytes = new byte[32]; random.nextBytes(bytes); try { Files.write(path, bytes, StandardOpenOption.CREATE_NEW); } catch (FileAlreadyExistsException ignored) { } }
        byte[] key = Files.readAllBytes(path); if (key.length != 32) throw new IllegalStateException(); return key;
    }
    private String encrypt(String value) {
        try { byte[] iv = new byte[12]; random.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey(), "AES"), new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); byte[] combined = new byte[iv.length + encrypted.length]; System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length); return Base64.getEncoder().encodeToString(combined); }
        catch (Exception e) { throw new IllegalStateException("密钥加密失败"); }
    }
    private String decrypt(String value) {
        try { byte[] bytes = Base64.getDecoder().decode(value); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), "AES"), new GCMParameterSpec(128, Arrays.copyOf(bytes, 12))); return new String(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalStateException("密钥无法解密，请重新填写并保存；请勿删除本机 master.key"); }
    }
    public static class Data { public String activeId; public List<Stored> profiles = new ArrayList<>(); }
    public static class Stored { public String id, name, provider, baseUrl, model, encryptedKey, testedAt; }
}
