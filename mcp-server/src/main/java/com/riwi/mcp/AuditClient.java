package com.riwi.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Read-only adapter to audit-service. The MCP server never persists or mutates data. */
public final class AuditClient {
    private final HttpClient http;
    private final String baseUrl;
    private final String bearerToken;

    public AuditClient(HttpClient http, String baseUrl, String bearerToken) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.bearerToken = bearerToken;
    }

    public String byCorrelation(String correlationId) throws Exception {
        String encoded = java.net.URLEncoder.encode(correlationId, java.nio.charset.StandardCharsets.UTF_8);
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/audit/events/correlation/" + encoded)).GET();
        if (bearerToken != null && !bearerToken.isBlank()) builder.header("Authorization", "Bearer " + bearerToken);
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new IllegalStateException("audit-service returned HTTP " + response.statusCode());
        return response.body();
    }
}
