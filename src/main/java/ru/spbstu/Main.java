package ru.spbstu;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.config.AppConfig;

import java.nio.charset.Charset;

public class Main {
    public static void main(String[] args) {
        System.out.println("Default Charset: " + Charset.defaultCharset());
        System.out.println("File encoding: " + System.getProperty("file.encoding"));
        System.out.println("Console test: Привет мир!");
        try {
            var context = new AnnotationConfigApplicationContext(AppConfig.class);
            context.start();
            System.out.println("\nThe bot is running...");
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            StatusPrinter.print(lc);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                context.close();
            }));

            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("Error starting the bot: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
