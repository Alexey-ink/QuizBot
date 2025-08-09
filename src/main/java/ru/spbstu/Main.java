package ru.spbstu;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import ru.spbstu.config.HibernateConfig;
import ru.spbstu.config.AppConfig;
import ru.spbstu.model.User;

public class Main {
    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("\nThe bot is running...");

            SessionFactory sf = HibernateConfig.getSessionFactory();
            try (Session session = sf.openSession()) {
                session.beginTransaction();
                var users = session.createQuery("from User", User.class).list();
                System.out.println("Users count: " + users.size());
                session.getTransaction().commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
