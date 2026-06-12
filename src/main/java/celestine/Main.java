package celestine;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {

    static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("CLAUDE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: CLAUDE_API_KEY environment variable is not set.");
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/annotate", new AnnotateHandler(apiKey));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Celestine running at http://localhost:" + PORT);
    }
}
