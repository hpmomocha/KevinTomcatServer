package cn.com.kevin.engine;

import cn.com.kevin.Config;
import cn.com.kevin.connector.HttpExchangeRequest;
import cn.com.kevin.engine.support.Attributes;
import cn.com.kevin.engine.support.HttpHeaders;
import cn.com.kevin.engine.support.Parameters;
import cn.com.kevin.utils.HttpUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

public class HttpServletRequestImpl implements HttpServletRequest {
    final Config config;
    // 引用ServletContextImpl:
    final ServletContextImpl servletContext;
    final HttpExchangeRequest exchangeRequest;
    // 引用HttpServletResponse:
    final HttpServletResponse response;
    final String method;

    final HttpHeaders headers;
    final Parameters parameters;

    String characterEncoding;
    int contentLength = 0;
    String requestId = null;
    Attributes attributes = new Attributes();

    //
    private boolean inputCalled;

    public HttpServletRequestImpl(Config config, ServletContextImpl servletContext,
                                  HttpExchangeRequest exchangeRequest, HttpServletResponse response) {
        this.config = config;
        this.servletContext = servletContext;
        this.exchangeRequest = exchangeRequest;
        this.response = response;

        this.characterEncoding = config.server.requestEncoding;
        this.method = exchangeRequest.getRequestMethod();
        this.headers = new HttpHeaders(exchangeRequest.getRequestHeaders());
        this.parameters = new Parameters(exchangeRequest, this.characterEncoding);

        if ("POST".equals(this.method) || "PUT".equals(this.method) || "DELETE".equals(this.method) || "PATCH".equals(this.method)) {
            this.contentLength = getIntHeader("Content-Length");
        }
    }

    @Override
    public String getMethod() {
        return exchangeRequest.getRequestMethod();
    }

    @Override
    public String getRequestURI() {
        return this.exchangeRequest.getRequestURI().getPath();
    }

    @Override
    public String getParameter(String name) {
        return this.parameters.getParameter(name);
    }

    @Override
    public String[] getParameterValues(String name) {
        return this.parameters.getParameterValues(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return this.parameters.getParameterMap();
    }

    // 从请求中取出 JSESSIONID
    @Override
    public HttpSession getSession(boolean create) {
        String sessionId = null;
        Cookie[] cookies = getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }

        if (sessionId == null && !create) {
            return null;
        }

        if (sessionId == null) {
            if (this.response.isCommitted()) {
                throw new IllegalStateException("Cannot create session for response is commited.");
            }
            sessionId = UUID.randomUUID().toString();
            // set cookie:
            String cookieValue = "JSESSIONID=" + sessionId + "; Path=/; SameSite=Strict; HttpOnly";
            this.response.addHeader("Set-Cookie", cookieValue);
        }

        return this.servletContext.sessionManager.getSession(sessionId);
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        throw new UnsupportedOperationException("changeSessionId() is not supported.");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return true;
    }

    @Override
    public Cookie[] getCookies() {
        String cookieValue = this.getHeader("Cookie");
        return HttpUtils.parseCookies(cookieValue);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (this.inputCalled) {
            this.inputCalled = Boolean.TRUE;
            return new ServletInputStreamImpl(this.exchangeRequest.getRequestBody());
        }
        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (this.inputCalled) {
            this.inputCalled = Boolean.TRUE;
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.exchangeRequest.getRequestBody()), StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
    }

    // header operations //////////////////////////////////////////////////////

    @Override
    public long getDateHeader(String name) {
        return this.headers.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return this.headers.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> hs = this.headers.getHeaders(name);
        if (hs == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(hs);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(this.headers.getHeaderNames());
    }

    @Override
    public int getIntHeader(String name) {
        return this.headers.getIntHeader(name);
    }

    // not implemented yet:
    @Override
    public String getAuthType() {
        return null;
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        // root context path:
        return "";
    }

    @Override
    public String getQueryString() {
        return this.exchangeRequest.getRequestURI().getRawQuery();
    }

    @Override
    public String getRemoteUser() {
        return null;
    }

    @Override
    public boolean isUserInRole(String s) {
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public StringBuffer getRequestURL() {
        return null;
    }

    @Override
    public String getServletPath() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
        return false;
    }

    @Override
    public void login(String s, String s1) throws ServletException {

    }

    @Override
    public void logout() throws ServletException {

    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        // not support multipart:
        return List.of();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        // not support multipart:
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
        // not support websocket:
        return null;
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
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.characterEncoding = env;
        this.parameters.setCharset(env);
    }

    @Override
    public int getContentLength() {
        return this.contentLength;
    }

    @Override
    public long getContentLengthLong() {
        return 0;
    }

    @Override
    public String getContentType() {
        return getHeader("Content-Type");
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return this.parameters.getParameterNames();
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        return "http";
    }

    @Override
    public String getServerName() {
        return "localhost";
    }

    @Override
    public int getServerPort() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getPort();
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress address = this.exchangeRequest.getRemoteAddress();
        return address.getHostString();
    }

    @Override
    public String getRemoteHost() {
        // avoid DNS lookup by IP:
        return getRemoteAddr();
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else {
            Object oldValue = this.attributes.setAttribute(name, value);
            if (oldValue == null) {
                this.servletContext.invokeServletRequestAttributeAdded(this, name, value);
            } else {
                this.servletContext.invokeServletRequestAttributeReplaced(this, name, value);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldValue = this.attributes.removeAttribute(name);
        this.servletContext.invokeServletRequestAttributeRemoved(this, name, oldValue);
    }
    @Override
    public Locale getLocale() {
        return Locale.CHINA;
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(List.of(Locale.CHINA, Locale.US));
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        // do not support request dispatcher:
        return null;
    }

    @Override
    public String getRealPath(String s) {
        return null;
    }

    @Override
    public int getRemotePort() {
        InetSocketAddress address = this.exchangeRequest.getRemoteAddress();
        return address.getPort();
    }

    @Override
    public String getLocalName() {
        // avoid DNS lookup:
        return getLocalAddr();
    }

    @Override
    public String getLocalAddr() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getHostString();
    }

    @Override
    public int getLocalPort() {
        InetSocketAddress address = this.exchangeRequest.getLocalAddress();
        return address.getPort();
    }

    @Override
    public ServletContext getServletContext() {
        return this.servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String toString() {
        return String.format("HttpServletRequestImpl@%s[%s:%s]", Integer.toHexString(hashCode()), getMethod(), getRequestURI());
    }
}
