package celestine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String resource = resolveResource(exchange.getRequestURI().getPath());
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                byte[] msg = ("Not found: " + resource).getBytes();
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(msg);
                }
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(resource));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // Maps a request path to a bundled resource. "/" serves index.html; only a
    // whitelist of known static pages is exposed, everything else falls back to it.
    private String resolveResource(String path) {
        if ("/frames.html".equals(path)) return "/frames.html";
        return "/index.html";
    }

    private String contentType(String resource) {
        if (resource.endsWith(".html")) return "text/html; charset=UTF-8";
        if (resource.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (resource.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        return "application/octet-stream";
    }
}
