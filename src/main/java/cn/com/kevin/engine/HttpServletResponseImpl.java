package cn.com.kevin.engine;

import cn.com.kevin.Config;
import cn.com.kevin.connector.HttpExchangeResponse;
import cn.com.kevin.engine.support.HttpHeaders;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HttpServletResponseImpl implements HttpServletResponse {
    final Config config;
    final HttpExchangeResponse exchangeResponse;
    final HttpHeaders headers;

    int status = 200;
    int bufferSize = 1024;
    // 在使用Response对象的PrintWriter对象与ServletOutputStream对象时，一定需要注意他们是不能同时使用的
    Boolean callOutput = null;
    // ServletOutputStream 向浏览器输出的是二进制数据，是字节流，可以处理任意类型的数据
    ServletOutputStream output;
    // PrintWriter 输出的是字符型数据，是字符流。
    PrintWriter writer;
    String contentType;
    String characterEncoding;
    long contentLength = 0;
    Locale locale = null;
    List<Cookie> cookies = null;
    boolean committed = false;

    public HttpServletResponseImpl(Config config, HttpExchangeResponse exchangeResponse) {
        this.config = config;
        this.exchangeResponse = exchangeResponse;
        this.headers = new HttpHeaders(exchangeResponse.getResponseHeaders());
        this.characterEncoding = config.server.responseEncoding;
        this.setContentType("text/html");
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (callOutput == null) {
            commitHeaders(0);
            this.output = new ServletOutputStreamImpl(this.exchangeResponse.getResponseBody());
            this.callOutput = Boolean.TRUE;
            return this.output;
        }
        if (callOutput.booleanValue()) {
            return this.output;
        }
        throw new IllegalStateException("Cannot open output stream when writer is opened.");
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (callOutput == null) {
            commitHeaders(0);
            this.writer = new PrintWriter(exchangeResponse.getResponseBody(), true, StandardCharsets.UTF_8);
            this.callOutput = Boolean.FALSE;
            return this.writer;
        }
        if (!callOutput.booleanValue()) {
            return this.writer;
        }
        throw new IllegalStateException("Cannot open writer when output stream is opened.");
    }

    @Override
    public void setContentLength(int len) {
        this.contentLength = len;
    }

    @Override
    public void setContentLengthLong(long len) {
        this.contentLength = len;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
        setHeader("Content-Type", type);
    }

    @Override
    public void setBufferSize(int size) {
        if (this.output != null) {
            throw new IllegalStateException("Output stream or writer is opened.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (this.callOutput == null) {
            throw new IllegalStateException("Output stream or writer is not opened.");
        }
        if (this.callOutput.booleanValue()) {
            this.output.flush();
        } else {
            this.writer.flush();
        }
    }

    @Override
    public void resetBuffer() {
        checkNotCommitted();
    }

    @Override
    public boolean isCommitted() {
        return this.committed;
    }

    @Override
    public void reset() {
        checkNotCommitted();
        this.status = 200;
        this.headers.clearHeaders();
    }

    @Override
    public void addCookie(Cookie cookie) {
        if (this.cookies == null) {
            this.cookies = new ArrayList<>();
        }
        this.cookies.add(cookie);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        checkNotCommitted();
        this.setStatus(sc);
        commitHeaders(-1);
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, "Error");
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        checkNotCommitted();
        this.status = 302;
        this.headers.setHeader("Location", location);
        commitHeaders(-1);
    }

    @Override
    public void setStatus(int status) {
        checkNotCommitted();
        this.status = status;
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    // header operations //////////////////////////////////////////////////////

    @Override
    public boolean containsHeader(String name) {
        return this.headers.containsHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return this.headers.getHeader(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> hs = this.headers.getHeaders(name);
        if (hs == null) {
            return List.of();
        }
        return hs;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return Collections.unmodifiableSet(this.headers.getHeaderNames());
    }

    @Override
    public void setDateHeader(String name, long date) {
        checkNotCommitted();
        this.headers.setDateHeader(name, date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        checkNotCommitted();
        this.headers.addDateHeader(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
        checkNotCommitted();
        this.headers.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        checkNotCommitted();
        this.headers.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        checkNotCommitted();
        this.headers.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        checkNotCommitted();
        this.headers.addIntHeader(name, value);
    }

    void commitHeaders(long length) throws IOException {
        this.exchangeResponse.sendResponseHeaders(this.status, length);
        this.committed = true;
    }

    public void cleanup() throws IOException {
        if (this.callOutput != null) {
            if (this.callOutput.booleanValue()) {
                this.output.close();
            } else {
                this.writer.close();
            }
        }
    }

    // check if not committed:
    void checkNotCommitted() {
        if (this.committed) {
            throw new IllegalStateException("Response is committed.");
        }
    }

    // not implemented yet
    @Override
    public String encodeURL(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public String encodeUrl(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public String encodeRedirectUrl(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public void setStatus(int i, String s) {

    }

    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
    }

    @Override
    public void setLocale(Locale locale) {
        checkNotCommitted();
        this.locale = locale;
    }

    @Override
    public Locale getLocale() {
        return this.locale == null ? Locale.getDefault() : this.locale;
    }
}
