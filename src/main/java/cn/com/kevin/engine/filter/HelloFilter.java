package cn.com.kevin.engine.filter;


import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

//@WebFilter(urlPatterns = "/hello")
public class HelloFilter implements Filter {
    final Logger logger = LoggerFactory.getLogger(getClass());

    Set<String> names = Set.of("Bob", "Alice", "Tom", "Jerry");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String name = req.getParameter("name");
        logger.info("Check parameter name = {}", name);
        if (name != null && names.contains(name)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Access denied: name = {}", name);
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.sendError(403, "Forbidden");
        }
    }
}
