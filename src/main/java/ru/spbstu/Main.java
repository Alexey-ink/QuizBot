package ru.spbstu;

import org.eclipse.jetty.server.Server;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.config.AppConfig;
import ru.spbstu.config.JettyServer;

public class Main {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext rootContext = null;
        Server jetty = null;
        try {
            rootContext = new AnnotationConfigApplicationContext(AppConfig.class);
            rootContext.start();

            int port = Integer.parseInt(System.getProperty("http.port", "8080"));
            jetty = JettyServer.start(rootContext, port);

            System.out.println("The bot is running...");

            Server finalJetty = jetty;
            AnnotationConfigApplicationContext finalRoot = rootContext;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                try {
                    if (finalJetty.isRunning()) {
                        finalJetty.stop();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (finalRoot != null) {
                        finalRoot.close();
                    }
                }
            }));

            jetty.join();

        } catch (Exception e) {
            System.err.println("Error starting app: " + e.getMessage());
            e.printStackTrace();
            if (jetty != null) {
                try { jetty.stop(); } catch (Exception ex) { ex.printStackTrace(); }
            }
            if (rootContext != null) rootContext.close();
            System.exit(1);
        }
    }
}
