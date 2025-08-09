package ru.spbstu.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import ru.spbstu.model.Question;
import ru.spbstu.model.QuestionOption;
import ru.spbstu.model.Tag;
import ru.spbstu.model.User;

import java.util.Properties;

public class HibernateConfig {

    private static final SessionFactory sessionFactory;
    private static final Dotenv dotenv = Dotenv.load();

    static {
        try {
            Configuration configuration = new Configuration();

            Properties settings = new Properties();

            settings.put("hibernate.connection.driver_class", "org.postgresql.Driver");
            settings.put("hibernate.connection.url", dotenv.get("DB_URL"));
            settings.put("hibernate.connection.username", dotenv.get("DB_USER"));
            settings.put("hibernate.connection.password", dotenv.get("DB_PASSWORD"));
            settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

            // Автосоздание/обновление таблиц
            settings.put("hibernate.hbm2ddl.auto", "update");

            configuration.setProperties(settings);

            configuration.addAnnotatedClass(User.class);
            configuration.addAnnotatedClass(Tag.class);
            configuration.addAnnotatedClass(Question.class);
            configuration.addAnnotatedClass(QuestionOption.class);

            ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties()).build();

            sessionFactory = configuration.buildSessionFactory(serviceRegistry);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка инициализации Hibernate", e);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}

