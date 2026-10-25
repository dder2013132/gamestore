package com.gamestore.service;

import java.io.IOException;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Small, explicit JSON contract for the progressively enhanced wishlist forms. */
public final class WishlistJson {
    private WishlistJson() { }
    public static boolean requested(HttpServletRequest request) {
        String accept=request.getHeader("Accept");
        return accept!=null&&accept.toLowerCase(Locale.ROOT).contains("application/json");
    }
    public static void success(HttpServletResponse response,int gameId,boolean saved) throws IOException {
        prepare(response,200);
        response.getWriter().write("{\"gameId\":"+gameId+",\"saved\":"+saved+"}");
    }
    public static void error(HttpServletResponse response,int status,String code,String message) throws IOException {
        prepare(response,status);
        response.getWriter().write("{\"error\":"+quote(code)+",\"message\":"+quote(message)+"}");
    }
    private static void prepare(HttpServletResponse response,int status) {
        response.setStatus(status); response.setCharacterEncoding("UTF-8"); response.setContentType("application/json");
        response.setHeader("Cache-Control","no-store");
    }
    private static String quote(String value) {
        StringBuilder result=new StringBuilder("\"");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            switch(c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if(c<32) result.append(String.format("\\u%04x",(int)c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
}
