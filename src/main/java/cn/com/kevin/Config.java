package cn.com.kevin;

import java.util.Map;

public class Config {
    // 顶层 server
    public Server server;

    public static class Server {
        public String host;
        public Integer port;
        public Integer backlog;
        public String requestEncoding;
        public String responseEncoding;
        public String name;
        public String mimeDefault;
        public int threadPoolSize;
        public boolean enableVirtualThread;
        public Map<String, String> mimeTypes;
        public WebApp webApp;
        public ForwardedHeaders forwardedHeaders;
    }

    public static class WebApp {
        public String name;
        public boolean fileListings;
        public String virtualServerName;
        public String sessionCookieName;
        public Integer sessionTimeout;
    }

    public static class ForwardedHeaders {
        public String forwardedProto;
        public String forwardedHost;
        public String forwardedFor;
    }
}
