package com.reviewer.scout.service;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelConnection(String id, String name, String provider, String baseUrl, String model,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String apiKey) {
    @Override public String toString() { return "ModelConnection[id=" + id + ", provider=" + provider + ", model=" + model + ", apiKey=REDACTED]"; }
}
