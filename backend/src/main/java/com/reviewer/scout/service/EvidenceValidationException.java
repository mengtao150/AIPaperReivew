package com.reviewer.scout.service;

/** Machine-readable diagnostics, without source text or raw model output. */
public class EvidenceValidationException extends IllegalArgumentException {
    private final String code;
    private final String path;
    public EvidenceValidationException(String code,String path,String reason) {
        super("模型草稿未通过校验（"+path+"）："+reason+"。原有结果未被覆盖。");
        this.code=code;this.path=path;
    }
    public String getCode(){return code;}
    public String getPath(){return path;}
}
