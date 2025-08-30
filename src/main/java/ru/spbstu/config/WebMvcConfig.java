package ru.spbstu.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.context.annotation.Bean;
import java.util.List;
import org.springframework.http.converter.HttpMessageConverter;
import ru.spbstu.admin.AdminInterceptor;

@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {
        "ru.spbstu.api",
        "ru.spbstu.admin"
})
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminInterceptor adminInterceptor;

    public WebMvcConfig(AdminInterceptor adminInterceptor) {
        this.adminInterceptor = adminInterceptor;
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        return new MappingJackson2HttpMessageConverter();
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(mappingJackson2HttpMessageConverter());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**");
    }
}
