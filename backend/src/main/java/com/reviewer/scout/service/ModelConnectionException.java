package com.reviewer.scout.service;

/** Safe, actionable provider diagnostics. Never carries credentials or raw upstream bodies. */
public class ModelConnectionException extends IllegalStateException {
    private final String code;
    private final Integer upstreamStatus;
    private final String providerCode;
    public ModelConnectionException(String code, String message, Integer upstreamStatus) {
        this(code, message, upstreamStatus, null);
    }
    public ModelConnectionException(String code, String message, Integer upstreamStatus, String providerCode) {
        super(message); this.code = code; this.upstreamStatus = upstreamStatus; this.providerCode = providerCode;
    }
    public String getCode() { return code; }
    public Integer getUpstreamStatus() { return upstreamStatus; }
    public String getProviderCode() { return providerCode; }
}
