package com.riwi.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/** HU29 MCP entry point. Only explicitly registered read-only tools are exposed. */
public final class McpServerApplication {
    public static void main(String[] args) {
        var client = new AuditClient(HttpClient.newHttpClient(),
            env("AUDIT_SERVICE_URL", "http://localhost:8090"), System.getenv("MCP_BEARER_TOKEN"));
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        var schema = new McpSchema.JsonSchema("object",
            Map.of("correlationId", Map.of("type", "string", "minLength", 1)),
            List.of("correlationId"), false, Map.of(), Map.of());
        var tool = McpSchema.Tool.builder()
            .name("audit_by_correlation")
            .title("Audit trace by correlation")
            .description("Returns the chronological, read-only audit events for one correlationId.")
            .inputSchema(schema)
            .annotations(new McpSchema.ToolAnnotations("Read-only audit lookup", true, false, true, false, false))
            .build();
        McpServer.sync(transport)
            .serverInfo("skillbridge-incident-triage", "0.1.0")
            .instructions("Expose only deterministic, read-only incident evidence.")
            .tool(tool, (exchange, arguments) -> {
                try {
                    var correlationId = String.valueOf(arguments.get("correlationId"));
                    return new McpSchema.CallToolResult(client.byCorrelation(correlationId), false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult("audit lookup failed: " + e.getMessage(), true);
                }
            }).build();
    }

    private static String env(String key, String fallback) {
        var value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
