package ru.spbstu;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.config.AppConfig;

public class Main {
    public static void main(String[] args) {
        try {
            var context = new AnnotationConfigApplicationContext(AppConfig.class);
            context.start();
            System.out.println("\nThe bot is running...");
            
            // Ждем завершения работы
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                context.close();
            }));
            
            // Держим приложение запущенным
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("Error starting the bot: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
