package cn.com.kevin.engine;

import cn.com.kevin.utils.DateUtils;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager implements Runnable {
    final Logger logger = LoggerFactory.getLogger(getClass());

    // 引用ServletContext:
    final ServletContextImpl servletContext;

    // Session默认过期时间(秒):
    final int inactiveInterval;

    // Session 要考虑多线程，所以使用ConcurrentHashMap
    // 持有SessionID -> Session:
    final Map<String, HttpSessionImpl> sessions = new ConcurrentHashMap<>();

    public SessionManager(ServletContextImpl servletContext, int interval) {
        this.servletContext = servletContext;
        this.inactiveInterval = interval;
        Thread t = new Thread(this, "Session-Cleanup-Thread");
        t.setDaemon(true);
        t.start();
    }

    public HttpSession getSession(String sessionId) {
        HttpSessionImpl session = sessions.get(sessionId);
        if (session == null) {
            // Session未找到，创建一个新的Session:
            session = new HttpSessionImpl(servletContext, sessionId, inactiveInterval);
            sessions.put(sessionId, session);
            this.servletContext.invokeHttpSessionCreated(session);
        } else {
            // Session已存在，更新最后访问时间:
            session.lastAccessedTime = System.currentTimeMillis();
        }

        return session;
    }

    // 删除Session:
    public void remove(HttpSession session) {
        this.sessions.remove(session.getId());
        this.servletContext.invokeHttpSessionDestroyed(session);
    }

    @Override
    public void run() {
        for (;;) {
            try {
                Thread.sleep(60_000L);
            } catch (InterruptedException e){
                break;
            }
            long now = System.currentTimeMillis();
            for (String sessionId : sessions.keySet()) {
                HttpSession session = sessions.get(sessionId);
                if (session.getLastAccessedTime() + session.getMaxInactiveInterval() * 1000L < now) {
                    logger.warn("remove expired session: {}, last access time: {}", sessionId, DateUtils.formatDateTimeGMT(session.getLastAccessedTime()));
                    session.invalidate();
                }
            }
        }
    }
}
