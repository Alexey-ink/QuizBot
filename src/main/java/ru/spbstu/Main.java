package ru.spbstu;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.spbstu.config.AppConfig;
import ru.spbstu.model.User;

public class Main {
    public static void main(String[] args) {
        var context = new AnnotationConfigApplicationContext(AppConfig.class);
        System.out.println("\nThe bot is running...");
        context.registerShutdownHook();
    }
}
