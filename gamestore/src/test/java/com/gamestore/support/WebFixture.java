package com.gamestore.support;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Minimal servlet doubles, so authentication tests run without a DB or server. */
public class WebFixture {
    public final Map<String, Object> sessionValues = new HashMap<>();
    public final Map<String, Object> attributes = new HashMap<>();
    public final Map<String, String> parameters = new HashMap<>();
    public final Map<String, Object> contextValues = new HashMap<>();
    public Set<String> imagePaths = Set.of("/images/aetherbound.png", "/images/neon_drift.jpg");
    public boolean sessionExists;
    public boolean invalidated;
    public boolean sessionIdChanged;
    public boolean chained;
    public int timeout;
    public int status = 200;
    public String method = "GET";
    public String path = "/home";
    public String redirect;
    public String forwarded;

    public final HttpSession session = proxy(HttpSession.class, (name, args) -> {
        switch (name) {
            case "getAttribute": return sessionValues.get(args[0]);
            case "setAttribute": sessionValues.put((String) args[0], args[1]); return null;
            case "removeAttribute": sessionValues.remove(args[0]); return null;
            case "setMaxInactiveInterval": timeout = (Integer) args[0]; return null;
            case "invalidate": invalidated = true; sessionExists = false; sessionValues.clear(); return null;
            default: return null;
        }
    });

    public final ServletContext context = proxy(ServletContext.class, (name, args) -> {
        if (name.equals("getAttribute")) { return contextValues.get(args[0]); }
        if (name.equals("getResourcePaths")) { return imagePaths; }
        return null;
    });

    public final ServletConfig config = proxy(ServletConfig.class, (name, args) ->
            name.equals("getServletContext") ? context : null);

    public final HttpServletRequest request = proxy(HttpServletRequest.class, (name, args) -> {
        switch (name) {
            case "getMethod": return method;
            case "getServletPath": return path;
            case "getContextPath": return "/gamestore";
            case "getServletContext": return context;
            case "getParameter": return parameters.get(args[0]);
            case "getAttribute": return attributes.get(args[0]);
            case "setAttribute": attributes.put((String) args[0], args[1]); return null;
            case "getSession":
                if (args == null || args.length == 0 || Boolean.TRUE.equals(args[0])) { sessionExists = true; }
                return sessionExists ? session : null;
            case "changeSessionId": sessionIdChanged = true; return "new-session-id";
            case "getRequestDispatcher":
                String target = (String) args[0];
                return proxy(RequestDispatcher.class, (dispatchName, dispatchArgs) -> {
                    if (dispatchName.equals("forward")) { forwarded = target; }
                    return null;
                });
            default: return null;
        }
    });

    public final HttpServletResponse response = proxy(HttpServletResponse.class, (name, args) -> {
        switch (name) {
            case "sendRedirect": redirect = (String) args[0]; status = 302; return null;
            case "setStatus": case "sendError": status = (Integer) args[0]; return null;
            default: return null;
        }
    });

    private interface Invocation { Object call(String name, Object[] args); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
                (instance, method, args) -> {
                    Object result = invocation.call(method.getName(), args);
                    if (result != null || !method.getReturnType().isPrimitive()) { return result; }
                    if (method.getReturnType() == boolean.class) { return false; }
                    if (method.getReturnType() == long.class) { return 0L; }
                    if (method.getReturnType() == int.class) { return 0; }
                    return null;
                });
    }
}
