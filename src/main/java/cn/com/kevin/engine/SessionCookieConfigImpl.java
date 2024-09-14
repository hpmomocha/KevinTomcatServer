package cn.com.kevin.engine;

import cn.com.kevin.Config;
import cn.com.kevin.engine.support.Attributes;
import jakarta.servlet.SessionCookieConfig;

import java.util.Map;

/**
 * HSessionCookieConfig 实现类
 */
public class SessionCookieConfigImpl implements SessionCookieConfig {
    final Config config;
    final Attributes attributes = new Attributes();

    int maxAge;
    boolean httpOnly = true;
    boolean secure = false;
    String domain;
    String path;

    public SessionCookieConfigImpl(Config config) {
        this.config = config;
        this.maxAge = config.server.webApp.sessionTimeout * 60;
    }

    @Override
    public void setName(String name) {
        this.config.server.webApp.sessionCookieName = name;
    }

    @Override
    public String getName() {
        return this.config.server.webApp.sessionCookieName;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public String getDomain() {
        return this.domain;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public void setComment(String s) {
        // ignore
    }

    @Override
    public String getComment() {
        return null;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    @Override
    public boolean isHttpOnly() {
        return this.httpOnly;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    @Override
    public boolean isSecure() {
        return this.secure;
    }

    @Override
    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public int getMaxAge() {
        return this.maxAge;
    }

    public void setAttribute(String name, String value) {
        this.attributes.setAttribute(name, value);
    }

    public String getAttribute(String name) {
        return (String) this.attributes.getAttribute(name);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, String> getAttributes() {
        Map map = this.attributes.getAttributes();
        return (Map<String, String>) map;
    }
}
