package celestine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AnnotateHandler implements HttpHandler {

    private final ClaudeClient claude;

    public AnnotateHandler(String apiKey) {
        this.claude = new ClaudeClient(apiKey);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String sentence = parseFormParam(body, "sentence");
        if (sentence == null || sentence.isBlank()) {
            sendJson(exchange, 400, "{\"error\": \"Missing sentence parameter\"}");
            return;
        }

        try {
            String annotation = claude.annotate(sentence);
            sendJson(exchange, 200, annotation);
        } catch (Exception e) {
            String error = "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
            sendJson(exchange, 500, error);
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        sendResponse(exchange, status, json);
    }

    // Parses application/x-www-form-urlencoded body
    private String parseFormParam(String body, String key) {
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
