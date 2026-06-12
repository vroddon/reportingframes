package celestine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/index.html")) {
            if (in == null) {
                byte[] msg = "index.html not found".getBytes();
                exchange.sendResponseHeaders(404, msg.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(msg);
                }
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
