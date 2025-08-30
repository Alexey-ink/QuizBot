package ru.spbstu.config;

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

        handler.addServlet(servletHolder, "/*");
        server.setHandler(handler);

        server.start();
        System.out.println("\nJetty server started on port " + port);
        return server;
    }
}