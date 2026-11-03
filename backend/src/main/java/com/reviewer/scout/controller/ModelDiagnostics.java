package com.reviewer.scout.controller;

import com.reviewer.scout.service.ModelConnectionException;
import java.util.*;

final class ModelDiagnostics {
    private ModelDiagnostics() { }
    static Map<String, Object> describe(ModelConnectionException error) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", false); response.put("code", error.getCode()); response.put("error", error.getMessage());
        if (error.getUpstreamStatus() != null) response.put("upstreamStatus", error.getUpstreamStatus());
        if (error.getProviderCode() != null) response.put("providerCode", error.getProviderCode());
        response.put("retryable", Set.of("timeout", "network_error", "rate_limited", "upstream_unavailable", "interrupted").contains(error.getCode()));
        return response;
    }
}
