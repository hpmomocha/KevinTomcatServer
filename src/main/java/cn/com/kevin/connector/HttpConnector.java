package cn.com.kevin.connector;

import cn.com.kevin.Config;
import cn.com.kevin.engine.HttpServletRequestImpl;
import cn.com.kevin.engine.HttpServletResponseImpl;
import cn.com.kevin.engine.ServletContextImpl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

public class HttpConnector implements HttpHandler, AutoCloseable {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final Config config;
    final ClassLoader classLoader;

    final ServletContextImpl servletContext;
    final HttpServer httpServer;
    final Duration stopDelay = Duration.ofSeconds(5);

    public HttpConnector(Config config, String webRoot, Executor executor,
                         ClassLoader classLoader, List<Class<?>> autoScannedClasses) throws IOException {
        logger.info("starting Kevin's Tomcat http server at {}:{}...", config.server.host, config.server.port);
        this.config = config;
        this.classLoader = classLoader;

        // 为什么需要设置线程的ContextClassLoader？
        // 执行handle()方法的线程是由线程池提供的，线程池是HttpConnector创建的，因此，handle()方法内部加载的任何类都是由AppClassLoader加载的，
        // 而我们希望加载的类是由WebAppClassLoader从解压的war包中加载，此时，就需要设置线程的上下文类加载器。
        Thread.currentThread().setContextClassLoader(this.classLoader);

        ServletContextImpl ctx = new ServletContextImpl(classLoader, config, webRoot);
        // ServletContext 上下文初始化
        ctx.initialize(autoScannedClasses);

////        this.servletContext.initialize(List.of(IndexServlet.class, HelloServlet.class));
//        this.servletContext.initServlets(List.of(IndexServlet.class, HelloServlet.class, LoginServlet.class, LogoutServlet.class));
//        this.servletContext.initFilters(List.of(HelloFilter.class, LogFilter.class));
//        List<Class<? extends EventListener>> listenerClasses = List.of(HelloHttpSessionAttributeListener.class, HelloHttpSessionListener.class,
//                HelloServletContextAttributeListener.class, HelloServletContextListener.class, HelloServletRequestAttributeListener.class,
//                HelloServletRequestListener.class);
//        for (Class<? extends EventListener> listenerClass : listenerClasses) {
//            this.servletContext.addListener(listenerClass);
//        }

        this.servletContext = ctx;

        // start http server
        this.httpServer = HttpServer.create(new InetSocketAddress(config.server.host, config.server.port), config.server.backlog);
        // handle 方法处理 HTTP 请求
        this.httpServer.createContext("/", this);
        // 设置 HttpServer 线程池
        this.httpServer.setExecutor(executor);
        this.httpServer.start();

        logger.info("start Kevin's Tomcat http server at {}:{}", config.server.host, config.server.port);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        logger.info("{}: {}?{}", exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawQuery());

        var adapter = new HttpExchangeAdapter(exchange);
        var response = new HttpServletResponseImpl(this.config, adapter);
        var request = new HttpServletRequestImpl(this.config, this.servletContext, adapter, response);

        // process:
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            this.servletContext.process(request, response);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(null);
            response.cleanup();
        }
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        String html = "<h1>Hello, " + (name == null ? "world" : name) + ".</h1>";
        response.setContentType("text/html");
        PrintWriter writer = response.getWriter();
        writer.print(html);
        writer.close();
    }

    @Override
    public void close() throws Exception {
        this.servletContext.destroy();
        this.httpServer.stop((int) this.stopDelay.toSeconds());
    }
}
