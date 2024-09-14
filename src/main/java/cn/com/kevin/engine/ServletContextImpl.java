package cn.com.kevin.engine;

import cn.com.kevin.Config;
import cn.com.kevin.engine.mapping.FilterMapping;
import cn.com.kevin.engine.mapping.ServletMapping;
import cn.com.kevin.engine.servlet.DefaultServlet;
import cn.com.kevin.engine.support.Attributes;
import cn.com.kevin.utils.AnnoUtils;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ServletContextImpl implements ServletContext {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final ClassLoader classLoader;
    final Config config;
    // web root dir:
    final Path webRoot;
    // session manager:
    final SessionManager sessionManager;

    private boolean initialized = false;

    // servlet context attributes:
    private Attributes attributes = new Attributes(true);
    private SessionCookieConfig sessionCookieConfig;

    final Map<String, ServletRegistrationImpl> servletRegistrations = new HashMap<>();
    final Map<String, FilterRegistrationImpl> filterRegistrations = new HashMap<>();

    final Map<String, Servlet> nameToServlets = new HashMap<>();
    final Map<String, Filter> nameToFilters = new HashMap<>();

    final List<ServletMapping> servletMappings = new ArrayList<>();
    final List<FilterMapping> filterMappings = new ArrayList<>();
    Servlet defaultServlet;

    // Listener
    private List<ServletContextListener> servletContextListeners = null;
    private List<ServletContextAttributeListener> servletContextAttributeListeners = null;
    private List<ServletRequestListener> servletRequestListeners = null;
    private List<ServletRequestAttributeListener> servletRequestAttributeListeners = null;
    private List<HttpSessionAttributeListener> httpSessionAttributeListeners = null;
    private List<HttpSessionListener> httpSessionListeners = null;

    public ServletContextImpl(ClassLoader classLoader, Config config, String webRoot) {
        this.classLoader = classLoader;
        this.config = config;
        this.sessionCookieConfig = new SessionCookieConfigImpl(config);
        this.webRoot = Paths.get(webRoot).normalize().toAbsolutePath();
        this.sessionManager = new SessionManager(this, config.server.webApp.sessionTimeout);
        logger.info("set web root: {}", this.webRoot);
    }

    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // 获取请求路径:
        String path = request.getRequestURI();
        // 查找Servlet:
        Servlet servlet = null;
        for (ServletMapping mapping : this.servletMappings) {
            if (mapping.matches(path)) {
                servlet = mapping.servlet;
                break;
            }
        }

        if (servlet == null) {
            // 404 Not Found:
            PrintWriter pw = response.getWriter();
            pw.write("<h1>404 Not Found</h1><p>No mapping for URL: " + path + "</p>");
            pw.close();
            return;
        }

        // 查找Filter:
        List<Filter> enabledFilters = new ArrayList<>();
        for (FilterMapping filterMapping: this.filterMappings) {
            if (filterMapping.matches(path)) {
                enabledFilters.add(filterMapping.filter);
            }
        }
        Filter[] filters = enabledFilters.toArray(Filter[]::new);
        logger.atDebug().log("process {} by filter {}, servlet {}", path, Arrays.toString(filters), servlet);
        // 构造FilterChain实例:
        FilterChain chain = new FilterChainImpl(filters, servlet);
        // 由FilterChain处理:
        // 注意上述FilterChain不仅包含一个Filter[]数组，还包含一个Servlet，
        // 这样我们调用chain.doFilter()时，在FilterChain中最后一个处理请求的就是Servlet，这样设计可以简化我们实现FilterChain的代码：
        try {
            this.invokeServletRequestInitialized(request);
            chain.doFilter(request, response);
        } catch (ServletException e) {
            logger.error(e.getMessage(), e);
            throw new IOException(e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw e;
        } finally {
            this.invokeServletRequestDestroyed(request);
        }
    }

//    @Deprecated
//    public void initServlets(List<Class<?>> servletClasses) {
//        for (Class<?> c : servletClasses) {
//            WebServlet ws = c.getAnnotation(WebServlet.class);
//            if (ws != null) {
//                logger.info("auto register @WebServlet: {}", c.getName());
//                @SuppressWarnings("unchecked")
//                Class<? extends Servlet> clazz = (Class<? extends Servlet>) c;
//                ServletRegistration.Dynamic registration = this.addServlet(AnnoUtils.getServletName(clazz), clazz);
//                registration.addMapping(AnnoUtils.getServletUrlPatterns(clazz));
//                registration.setInitParameters(AnnoUtils.getServletInitParams(clazz));
//            }
//        }
//
//        // init servlets:
//        for (String name : this.servletRegistrations.keySet()) {
//            var registration = this.servletRegistrations.get(name);
//            try {
//                registration.servlet.init(registration.getServletConfig());
//                this.nameToServlets.put(name, registration.servlet);
//                for (String urlPattern : registration.getMappings()) {
//                    this.servletMappings.add(new ServletMapping(urlPattern, registration.servlet));
//                }
//                registration.initialized = true;
//            } catch (ServletException e) {
//                logger.error("init servlet failed: " + name + " / " + registration.servlet.getClass().getName(), e);
//            }
//        }
//
//        // important: sort mappings
//        Collections.sort(this.servletMappings);
//    }

//    @Deprecated
//    public void initFilters(List<Class<?>> filterClasses) {
//        for (Class<?> c : filterClasses) {
//            WebFilter webFilter = c.getAnnotation(WebFilter.class);
//            if (webFilter != null) {
//                logger.info("auto register @WebFilter: {}", c.getName());
//                @SuppressWarnings("unchecked")
//                Class<? extends Filter> clazz = (Class<? extends Filter>) c;
//                FilterRegistration.Dynamic registration = this.addFilter(AnnoUtils.getFilterName(clazz), clazz);
//                registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, AnnoUtils.getFilterUrlPatterns(clazz));
//                registration.setInitParameters(AnnoUtils.getFilterInitParams(clazz));
//            }
//        }
//
//        // init filters:
//        for (String name : this.filterRegistrations.keySet()) {
//            var registration = this.filterRegistrations.get(name);
//            try {
//                registration.filter.init(registration.getFilterConfig());
//                this.nameToFilters.put(name, registration.filter);
//                for (String urlPattern : registration.getUrlPatternMappings()) {
//                    this.filterMappings.add(new FilterMapping(name, urlPattern, registration.filter));
//                }
//                registration.initialized = true;
//            } catch (ServletException e) {
//                logger.error("init filter failed: " + name + " / " + registration.filter.getClass().getName(), e);
//            }
//        }
//    }

//    public void initialize(List<Class<?>> servletClasses) {
//        for (Class<?> c: servletClasses) {
//            // 获取 WebServlet 注解
//            WebServlet ws = c.getAnnotation(WebServlet.class);
//            if (ws != null) {
//                logger.info("auto register @WebServlet: {}", c.getName());
//                Class<? extends Servlet> clazz = (Class<? extends Servlet>) c;
//                ServletRegistration.Dynamic registration = this.addServlet(AnnoUtils.getServletName(clazz), clazz);
//                registration.addMapping(AnnoUtils.getServletUrlPatterns(clazz));
//                registration.setInitParameters(AnnoUtils.getServletInitParams(clazz));
//            }
//        }
//
//        // init servlets:
//        for (String name : this.servletRegistrations.keySet()) {
//            var registration  = this.servletRegistrations.get(name);
//            try {
//                registration .servlet.init(registration .getServletConfig());
//                this.nameToServlets.put(name, registration .servlet);
//                for (String urlPattern: registration.getMappings()) {
//                    this.servletMappings.add(new ServletMapping(urlPattern, registration.servlet));
//                }
//                registration.initialized = true;
//            } catch (ServletException e) {
//                logger.error("init servlet failed: " + name + " / " + registration.servlet.getClass().getName(), e);
//            }
//        }
//
//        // important: sort mappings
//        Collections.sort(this.servletMappings);
//    }

    @Override
    public String getContextPath() {
        // only support root context path:
        return "";
    }

    @Override
    public ServletContext getContext(String uriPath) {
        if ("".equals(uriPath)) {
            return this;
        }
        // all others are not exist:
        return null;
    }

    // Servlet API version: 5.0.0

    @Override
    public int getMajorVersion() {
        return 5;
    }

    @Override
    public int getMinorVersion() {
        return 5;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public String getMimeType(String file) {
        String defaultMime = "application/octet-stream";
        Map<String, String> mimes = Map.of(".html", "text/html", ".txt", "text/plain", ".png", "image/png", ".jpg", "image/jpeg");
        int n = file.lastIndexOf('.');
        if (n == -1) {
            return defaultMime;
        }
        String ext = file.substring(n);
        return mimes.getOrDefault(ext, defaultMime);
    }

    @Override
    public Set<String> getResourcePaths(String s) {
        return null;
    }

    @Override
    public URL getResource(String s) throws MalformedURLException {
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String s) {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String s) {
        return null;
    }

    @Override
    public Servlet getServlet(String s) throws ServletException {
        return null;
    }

    @Override
    public Enumeration<Servlet> getServlets() {
        return null;
    }

    @Override
    public Enumeration<String> getServletNames() {
        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Override
    public void log(Exception e, String s) {

    }

    @Override
    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public String getRealPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Path loc = this.webRoot.resolve(path).normalize();
        if (loc.startsWith(this.webRoot)) {
            return loc.toString();
        }
        return null;
    }

    @Override
    public String getServerInfo() {
        return this.config.server.name;
    }

    @Override
    public String getInitParameter(String s) {
        // no init parameters:
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        // no init parameters:
        return Collections.emptyEnumeration();
    }

    @Override
    public boolean setInitParameter(String s, String s1) {
        throw new UnsupportedOperationException("setInitParameter");
    }

    @Override
    public Object getAttribute(String name) {
        return this.attributes.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return this.attributes.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else {
            Object old = this.attributes.setAttribute(name, value);
            if (old == null) {
                this.invokeServletContextAttributeAdded(name, value);
            } else {
                this.invokeServletContextAttributeReplaced(name, value);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object old = this.attributes.removeAttribute(name);
        this.invokeServletContextAttributeRemoved(name, old);
    }

    @Override
    public String getServletContextName() {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class name is null or empty.");
        }
        Servlet servlet = null;

        try {
            Class<? extends Servlet> clazz = createInstance(className);
            servlet = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return addServlet(name, servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, Servlet servlet) {
        if (name == null) {
            throw new IllegalArgumentException("name is null.");
        }
        if (servlet == null) {
            throw new IllegalArgumentException("servlet is null.");
        }
        var registration = new ServletRegistrationImpl(this, name, servlet);
        this.servletRegistrations.put(name, registration);
        return registration;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, Class<? extends Servlet> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("class is null.");
        }
        Servlet servlet = null;
        try {
            servlet = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addServlet(name, servlet);
    }

    private <T> T createInstance(String className) throws ServletException {
        Class<T> clazz;
        try {
            clazz = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found.", e);
        }

        return createInstance(clazz);
    }

    private <T> T createInstance(Class<T> clazz) throws ServletException {
        try {
            Constructor<T> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException  e) {
            throw new ServletException("Cannot instantiate class " + clazz.getName(), e);
        }
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        return createInstance(clazz);
    }

    @Override
    public ServletRegistration getServletRegistration(String name) {
        return this.servletRegistrations.get(name);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Map.copyOf(this.servletRegistrations);
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String s, String s1) {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class name is null or empty.");
        }
        Filter filter = null;
        try {
            Class<? extends Filter> clazz = createInstance(className);
            filter = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }

        return addFilter(name, filter);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, Filter filter) {
        if (name == null) {
            throw new IllegalArgumentException("name is null.");
        }
        if (filter == null) {
            throw new IllegalArgumentException("filter is null.");
        }
        var registration = new FilterRegistrationImpl(this, name, filter);
        this.filterRegistrations.put(name, registration);
        return registration;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, Class<? extends Filter> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("class is null.");
        }
        Filter filter = null;
        try {
            filter = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addFilter(name, filter);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        return createInstance(clazz);
    }

    @Override
    public FilterRegistration getFilterRegistration(String name) {
        return this.filterRegistrations.get(name);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return Map.copyOf(this.filterRegistrations);
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> set) {

    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return null;
    }

    @Override
    public void addListener(String className) {
        EventListener listener = null;
        try {
            Class<EventListener> clazz = createInstance(className);
            listener = createInstance(clazz);
        } catch (ServletException  e) {
            throw new RuntimeException(e);
        }
        addListener(listener);
    }

    @Override
    public <T extends EventListener> void addListener(T t) {
        if (t instanceof ServletContextListener listener) {
            if (this.servletContextListeners == null) {
                this.servletContextListeners = new ArrayList<>();
            }
            this.servletContextListeners.add(listener);
        } else if (t instanceof ServletContextAttributeListener listener) {
            if (this.servletContextAttributeListeners == null) {
                this.servletContextAttributeListeners = new ArrayList<>();
            }
            this.servletContextAttributeListeners.add(listener);
        } else if (t instanceof ServletRequestListener listener) {
            if (this.servletRequestListeners == null) {
                this.servletRequestListeners = new ArrayList<>();
            }
            this.servletRequestListeners.add(listener);
        } else if (t instanceof ServletRequestAttributeListener listener) {
            if (this.servletRequestAttributeListeners == null) {
                this.servletRequestAttributeListeners = new ArrayList<>();
            }
            this.servletRequestAttributeListeners.add(listener);
        } else if (t instanceof HttpSessionAttributeListener listener) {
            if (this.httpSessionAttributeListeners == null) {
                this.httpSessionAttributeListeners = new ArrayList<>();
            }
            this.httpSessionAttributeListeners.add(listener);
        } else if (t instanceof HttpSessionListener listener) {
            if (this.httpSessionListeners == null) {
                this.httpSessionListeners = new ArrayList<>();
            }
            this.httpSessionListeners.add(listener);
        } else {
            throw new IllegalArgumentException("Unsupported listener: " + t.getClass().getName());
        }
    }

    @Override
    public void addListener(Class<? extends EventListener> clazz) {
        EventListener listener = null;
        try {
            listener = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        addListener(listener);
    }

    // invoke listeners ///////////////////////////////////////////////////////
    void invokeServletContextAttributeAdded(String name, Object value) {
        logger.info("invoke ServletContextAttributeAdded: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeServletContextAttributeRemoved(String name, Object value) {
        logger.info("invoke ServletContextAttributeRemoved: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeServletContextAttributeReplaced(String name, Object value) {
        logger.info("invoke ServletContextAttributeReplaced: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeServletRequestAttributeAdded(HttpServletRequest request, String name, Object value) {
        logger.info("invoke ServletRequestAttributeAdded: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeServletRequestAttributeRemoved(HttpServletRequest request, String name, Object value) {
        logger.info("invoke ServletRequestAttributeRemoved: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeServletRequestAttributeReplaced(HttpServletRequest request, String name, Object value) {
        logger.info("invoke ServletRequestAttributeReplaced: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeHttpSessionAttributeAdded(HttpSession session, String name, Object value) {
        logger.info("invoke HttpSessionAttributeAdded: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeHttpSessionAttributeRemoved(HttpSession session, String name, Object value) {
        logger.info("invoke ServletContextAttributeRemoved: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeHttpSessionAttributeReplaced(HttpSession session, String name, Object value) {
        logger.info("invoke ServletContextAttributeReplaced: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeServletRequestInitialized(HttpServletRequest request) {
        logger.info("invoke ServletRequestInitialized: request = {}", request);
        if (this.servletRequestListeners != null) {
            var event = new ServletRequestEvent(this, request);
            for (var listener : this.servletRequestListeners) {
                listener.requestInitialized(event);
            }
        }
    }

    void invokeServletRequestDestroyed(HttpServletRequest request) {
        logger.info("invoke ServletRequestDestroyed: request = {}", request);
        if (this.servletRequestListeners != null) {
            var event = new ServletRequestEvent(this, request);
            for (var listener : this.servletRequestListeners) {
                listener.requestDestroyed(event);
            }
        }
    }

    void invokeHttpSessionCreated(HttpSession session) {
        logger.info("invoke HttpSessionCreated: session = {}", session);
        if (this.httpSessionListeners != null) {
            var event = new HttpSessionEvent(session);
            for (var listener : this.httpSessionListeners) {
                listener.sessionCreated(event);
            }
        }
    }

    void invokeHttpSessionDestroyed(HttpSession session) {
        logger.info("invoke HttpSessionDestroyed: session = {}", session);
        if (this.httpSessionListeners != null) {
            var event = new HttpSessionEvent(session);
            for (var listener : this.httpSessionListeners) {
                listener.sessionDestroyed(event);
            }
        }
    }

    void invokeServletContextInitialized() {
        logger.debug("invoke ServletContextInitialized: {}", this);

        if (this.servletContextListeners != null) {
            var event = new ServletContextEvent(this);
            for (var listener: this.servletContextListeners) {
                /**
                 * 当Servlet 容器启动Web 应用时调用该方法。在调用完该方法之后，容器再对Filter 初始化，
                 * 并且对那些在Web 应用启动时就需要被初始化的Servlet 进行初始化。
                 */
                listener.contextInitialized(event);
            }
        }
    }

    void invokeServletContextDestroyed() {
        logger.debug("invoke ServletContextDestroyed: {}", this);
        if (this.servletContextListeners != null) {
            var event = new ServletContextEvent(this);
            for (var listener : this.servletContextListeners) {
                listener.contextDestroyed(event);
            }
        }
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> aClass) throws ServletException {
        return null;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return null;
    }

    @Override
    public void declareRoles(String... strings) {

    }

    @Override
    public String getVirtualServerName() {
        return null;
    }

    @Override
    public int getSessionTimeout() {
        return 0;
    }

    @Override
    public void setSessionTimeout(int i) {

    }

    @Override
    public String getRequestCharacterEncoding() {
        return null;
    }

    @Override
    public void setRequestCharacterEncoding(String s) {

    }

    @Override
    public String getResponseCharacterEncoding() {
        return null;
    }

    @Override
    public void setResponseCharacterEncoding(String s) {

    }

    // 动态注册 Filter，Servlet，Listener
    // 所谓的动态注册，也只能在初始化时进行注册。在运行时为了安全原因，无法完成注册。
    // 在初始化情况下的注册Servlet组件（servlet、filter、listener）有两种方法：
    // 1. 实现ServletContextListener接口,在contextInitialized方法中完成注册.    ---> this.invokeServletContextInitialized();
    // 2. 在jar文件中放入实现ServletContainerInitializer接口的初始化器
    // 3. ServletContainerInitializer接口的方法实现在容器启动阶段为容器动态注册Servlet、Filter和listeners。
    //    容器会在应用的启动阶段，调用所有实现ServletContainerInitializer接口类中的onStartup()方法。
    //    而Spring　3.2中，则进一步简化了这点，只需要实现WebApplicationInitializer接口就可以了，其中提供了一个相关的实现类－－AbstractContextLoaderInitializer，它可以动态注册DispatcherServlet。
    // 4. 在SpringBoot中通过实现SpringBootServletInitializer接口来初始化容器
    // 以上内容来源： https://www.cnblogs.com/duanxz/archive/2013/02/25/2931952.html
    public void initialize(List<Class<?>> autoScannedClasses) {
        if (this.initialized) {
            throw new IllegalStateException("Cannot re-initialize.");
        }

        // register @WebListener:
        for (Class<?> c : autoScannedClasses) {
            if (c.isAnnotationPresent(WebListener.class)) {
                logger.info("auto register @WebListener: {}", c.getName());
                Class<? extends EventListener> clazz = (Class<? extends EventListener>) c;
                addListener(clazz);
            }
        }

        // 执行 ServletContextListener 处理
        this.invokeServletContextInitialized();

        // register @WebServlet and @WebFilter:
        for (Class<?> c : autoScannedClasses) {
            // @WebServlet
            WebServlet ws = c.getAnnotation(WebServlet.class);
            if (ws != null) {
                logger.info("auto register @WebServlet: {}", c.getName());
                @SuppressWarnings("unchecked")
                Class<? extends Servlet> clazz = (Class<? extends Servlet>) c;
                // 动态注册 servlet
                ServletRegistration.Dynamic registration = this.addServlet(AnnoUtils.getServletName(clazz), clazz);
                // servlet 映射
                registration.addMapping(AnnoUtils.getServletUrlPatterns(clazz));
                // servlet 初始化参数
                registration.setInitParameters(AnnoUtils.getServletInitParams(clazz));
            }
            // @WebFilter
            WebFilter wf = c.getAnnotation(WebFilter.class);
            if (wf != null) {
                logger.info("auto register @WebFilter: {}", c.getName());
                @SuppressWarnings("unchecked")
                Class<? extends Filter> clazz = (Class<? extends Filter>) c;
                // 动态注册 filter
                FilterRegistration.Dynamic registration = this.addFilter(AnnoUtils.getFilterName(clazz), clazz);
                // filter 映射
                registration.addMappingForUrlPatterns(AnnoUtils.getFilterDispatcherTypes(clazz), true, AnnoUtils.getFilterUrlPatterns(clazz));
                // filter 初始化参数
                registration.setInitParameters(AnnoUtils.getFilterInitParams(clazz));
            }
        }

        // init servlets while find default servlet:
        Servlet defaultServlet = null;
        for (String name : this.servletRegistrations.keySet()) {
            var registration = this.servletRegistrations.get(name);
            try {
                // 初始化 servlet
                registration.servlet.init(registration.getServletConfig());
                this.nameToServlets.put(name, registration.servlet);
                for (String urlPattern : registration.getMappings()) {
                    this.servletMappings.add(new ServletMapping(urlPattern, registration.servlet));
                    if (urlPattern.equals("/")) {
                        if (defaultServlet == null) {
                            defaultServlet = registration.servlet;
                            logger.info("set default servlet: " + registration.getClassName());
                        } else {
                            logger.warn("found duplicate default servlet: " + registration.getClassName());
                        }
                    }
                }
                registration.initialized = true;
            } catch (ServletException e) {
                logger.error("init servlet failed: " + name + " / " + registration.servlet.getClass().getName(), e);
            }
        }

        // 如果没有默认 servlet，并且如果启动了目录列表, 那么需要设置默认 Servlet
        if (defaultServlet == null && config.server.webApp.fileListings) {
            logger.info("no default servlet. auto register {}...", DefaultServlet.class.getName());
            defaultServlet = new DefaultServlet();
            try {
                defaultServlet.init(new ServletConfig() {
                    @Override
                    public String getServletName() {
                        return "DefaultServlet";
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return ServletContextImpl.this;
                    }

                    @Override
                    public String getInitParameter(String name) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getInitParameterNames() {
                        return Collections.emptyEnumeration();
                    }
                });
                this.servletMappings.add(new ServletMapping("/", defaultServlet));
            } catch (ServletException e) {
                logger.error("init default servlet failed.", e);
            }
            this.defaultServlet = defaultServlet;
        }
        // 2024-09-11
        // init filters:
        for (String name : this.filterRegistrations.keySet()) {
            var registration = this.filterRegistrations.get(name);
            try {
                registration.filter.init(registration.getFilterConfig());
                this.nameToFilters.put(name, registration.filter);
                for (String urlPattern : registration.getUrlPatternMappings()) {
                    this.filterMappings.add(new FilterMapping(name, urlPattern, registration.filter));
                }
                registration.initialized = true;
            } catch (ServletException e) {
                logger.error("init filter failed: " + name + " / " + registration.filter.getClass().getName(), e);
            }
        }

        // important: sort by servlet mapping:
        // 根据 servlet 的优先级排序
        Collections.sort(this.servletMappings);
        // important: sort by filter name:
        // Filter 执行顺序:
        // 1、注解方式（@WebFilter、@Component）：根据类名排序；
        // 2、xml配置方式：根据配置的先后顺序；
        // 3、config配置方式：优先根据设置的顺序，其次根据配置的先后顺序；
        // 4、注解+配置混合：优先配置，再注解；
        Collections.sort(this.filterMappings, (f1, f2) -> {
            int cmp = f1.filterName.compareTo(f2.filterName);
            if (cmp == 0) {
                cmp = f1.compareTo(f2);
            }
            return cmp;
        });

        this.initialized = true;
    }

    public void destroy() {
        // destroy filter and servlet:
        this.filterMappings.forEach(mapping -> {
            try {
                mapping.filter.destroy();
            } catch (Exception e) {
                logger.error("destroy filter '" + mapping.filter + "' failed.", e);
            }
        });

        this.servletMappings.forEach(mapping -> {
            try {
                mapping.servlet.destroy();
            } catch (Exception e) {
                logger.error("destroy servlet '" + mapping.servlet + "' failed.", e);
            }
        });

        // notify:
        this.invokeServletContextDestroyed();
    }

}
