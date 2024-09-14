package cn.com.kevin.engine;

import cn.com.kevin.engine.support.Attributes;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionContext;

import java.util.Enumeration;

public class HttpSessionImpl implements HttpSession {
    final ServletContextImpl servletContext;
    String sessionId;
    int maxInactiveInterval;
    long creationTime;
    long lastAccessedTime;
    Attributes attributes;

    public HttpSessionImpl(ServletContextImpl servletContext, String sessionId, int interval) {
        this.servletContext = servletContext;
        this.sessionId = sessionId;
        this.creationTime = this.lastAccessedTime = System.currentTimeMillis();
        this.attributes = new Attributes(true);
        setMaxInactiveInterval(interval);
    }
    @Override
    public long getCreationTime() {
        return this.creationTime;
    }

    @Override
    public String getId() {
        return this.sessionId;
    }

    @Override
    public long getLastAccessedTime() {
        return this.lastAccessedTime;
    }

    @Override
    public ServletContext getServletContext() {
        return this.servletContext;
    }

    @Override
    public void setMaxInactiveInterval(int inactiveInterval) {
        this.maxInactiveInterval = inactiveInterval;
    }

    @Override
    public int getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.getAttribute(name);
    }

    @Override
    public Object getValue(String s) {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        checkValid();
        return this.attributes.getAttributeNames();
    }

    @Override
    public String[] getValueNames() {
        return new String[0];
    }

    @Override
    public void setAttribute(String name, Object value) {
        checkValid();
        if (value == null) {
            removeAttribute(name);
        } else {
            Object oldValue = this.attributes.setAttribute(name, value);
            if (oldValue == null) {
                this.servletContext.invokeHttpSessionAttributeAdded(this, name, value);
            } else {
                this.servletContext.invokeHttpSessionAttributeReplaced(this, name, value);
            }
        }
    }

    @Override
    public void putValue(String s, Object o) {

    }

    @Override
    public void removeAttribute(String name) {
        checkValid();
        Object oldValue = this.attributes.removeAttribute(name);
        this.servletContext.invokeHttpSessionAttributeRemoved(this, name, oldValue);
    }

    @Override
    public void removeValue(String s) {
    }

    @Override
    public void invalidate() {
        checkValid();
        this.servletContext.sessionManager.remove(this);
        this.sessionId = null;
    }

    @Override
    public boolean isNew() {
        return this.creationTime == this.lastAccessedTime;
    }

    void checkValid() {
        if (this.sessionId == null) {
            throw new IllegalStateException("Session is already invalidated.");
        }
    }
}
