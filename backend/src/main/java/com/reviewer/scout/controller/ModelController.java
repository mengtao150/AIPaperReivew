package com.reviewer.scout.controller;

import com.reviewer.scout.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/models")
public class ModelController {
    private final ModelVault vault;
    private final ChatGateway gateway;
    public ModelController(ModelVault vault, ChatGateway gateway) { this.vault = vault; this.gateway = gateway; }
    @GetMapping public Map<String, Object> list(@RequestAttribute("workspace") String workspace) { return vault.list(workspace); }
    @PostMapping("/test") public Map<String, Object> test(@RequestAttribute("workspace") String workspace, @RequestBody ModelConnection draft) {
        try { return testConnection(workspace, draft, false); }
        catch (ModelConnectionException e) { return ModelDiagnostics.describe(e); }
    }
    @PostMapping public Map<String, Object> save(@RequestAttribute("workspace") String workspace, @RequestBody ModelConnection draft) { return testConnection(workspace, draft, true); }
    private Map<String, Object> testConnection(String workspace, ModelConnection draft, boolean persist) {
        ModelConnection resolved = vault.resolve(workspace, draft); long start = System.nanoTime();
        gateway.complete(resolved, List.of(Map.of("role", "user", "content", "Connection test. Reply only OK.")), 1024);
        Map<String, Object> result = new HashMap<>(); result.put("ok", true); result.put("latencyMs", (System.nanoTime() - start) / 1000000);
        if (persist) result.put("profile", vault.save(workspace, resolved));
        return result;
    }
    @PutMapping("/{id}/active") public Map<String, Boolean> activate(@RequestAttribute("workspace") String workspace, @PathVariable String id) { vault.activate(workspace, id); return Map.of("ok", true); }
    @DeleteMapping("/{id}") public Map<String, Boolean> delete(@RequestAttribute("workspace") String workspace, @PathVariable String id) { vault.delete(workspace, id); return Map.of("ok", true); }
}
