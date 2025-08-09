package ru.spbstu.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.spbstu.QuizBot;
import ru.spbstu.service.UserService;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@ComponentScan("ru.spbstu")
@EnableJpaRepositories(basePackages = "ru.spbstu.repository")
@EnableTransactionManagement
public class AppConfig {
    private final Dotenv dotenv = Dotenv.load();

    @Bean
    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(dotenv.get("DB_URL"));
        ds.setUsername(dotenv.get("DB_USER"));
        ds.setPassword(dotenv.get("DB_PASSWORD"));
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("ru.spbstu.model");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties props = new Properties();
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // props.put("hibernate.show_sql", "true");
        emf.setJpaProperties(props);

        return emf;
    }

    @Bean
    public JpaTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean emf) {
        var tm = new JpaTransactionManager();
        tm.setEntityManagerFactory(emf.getObject());
        return tm;
    }

    @Bean
    public QuizBot quizBot(UserService userService) {
        String token = dotenv.get("TELEGRAM_BOT_TOKEN");
        String username = dotenv.get("TELEGRAM_BOT_USERNAME");
        return new QuizBot(token, username, userService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(QuizBot bot) throws Exception {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        return api;
    }
}