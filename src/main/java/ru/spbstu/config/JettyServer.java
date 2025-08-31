package ru.spbstu.config;

import jakarta.servlet.MultipartConfigElement;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class JettyServer {

    public static Server start(ConfigurableApplicationContext rootContext, int port) throws Exception {
        Server server = new Server(port);

        AnnotationConfigWebApplicationContext webContext = new AnnotationConfigWebApplicationContext();
        webContext.register(WebMvcConfig.class);
        webContext.setParent(rootContext);

        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        handler.setContextPath("/");
        handler.addEventListener(new ContextLoaderListener(webContext));

        DispatcherServlet dispatcherServlet = new DispatcherServlet(webContext);
        ServletHolder servletHolder = new ServletHolder("dispatcher", dispatcherServlet);
        servletHolder.setInitParameter("throwExceptionIfNoHandlerFound", "true");

        String tmp = System.getProperty("java.io.tmpdir");
        long maxFileSize = 50L * 1024 * 1024;    // 50 MB
        long maxRequestSize = 200L * 1024 * 1024; // 200 MB
        int fileSizeThreshold = 0; // сразу в файл (0 — сразу в location)
        MultipartConfigElement multipartConfig =
                new MultipartConfigElement(tmp, maxFileSize, maxRequestSize, fileSizeThreshold);
        servletHolder.getRegistration().setMultipartConfig(multipartConfig);

        handler.addServlet(servletHolder, "/*");
        server.setHandler(handler);

        server.start();
        System.out.println("\nJetty server started on port " + port);
        return server;
    }
}