package celestine;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class Main {

    static final int PORT = 8612;

    public static void main(String[] args) throws Exception {
        printBanner();

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ERROR: ANTHROPIC_API_KEY environment variable is not set.");
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/annotate", new AnnotateHandler(apiKey));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("Celestine running at http://localhost:" + PORT);
    }

    // Reads version and build time from the jar manifest and prints them before startup.
    private static void printBanner() {
        String version = "dev";
        String buildTime = "unknown";
        try {
            Enumeration<URL> resources =
                    Main.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                try (var in = resources.nextElement().openStream()) {
                    Attributes attrs = new Manifest(in).getMainAttributes();
                    if ("celestine.Main".equals(attrs.getValue("Main-Class"))) {
                        version = attrs.getValue("Implementation-Version");
                        buildTime = attrs.getValue("Build-Time");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Running outside the packaged jar (e.g. from an IDE): fall back to defaults.
        }
        System.out.println("Celestine v" + version + " (built " + buildTime + ")");
    }
}
