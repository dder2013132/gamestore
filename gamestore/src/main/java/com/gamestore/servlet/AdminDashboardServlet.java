package com.gamestore.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeParseException;
import com.gamestore.repository.JdbcDashboardRepository;
import com.gamestore.model.OrderStatus;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

/** AuthFilter checks the current database administrator role. This servlet never changes data. */
@WebServlet(urlPatterns={"/admin","/admin/dashboard"})
public class AdminDashboardServlet extends HttpServlet {
    private final JdbcDashboardRepository dashboard;
    private final Clock clock;
    public AdminDashboardServlet() { this(new JdbcDashboardRepository(),Clock.systemDefaultZone()); }
    AdminDashboardServlet(JdbcDashboardRepository dashboard,Clock clock) { this.dashboard=dashboard; this.clock=clock; }
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws ServletException,IOException {
        if ("/admin".equals(request.getServletPath())) { response.sendRedirect(request.getContextPath()+"/admin/dashboard"); return; }
        String from=value(request,"from"),to=value(request,"to");
        request.setAttribute("fromDate",from); request.setAttribute("toDate",to);
        LocalDate today=LocalDate.now(clock); request.setAttribute("todayDate",today); request.setAttribute("last30Date",today.minusDays(29));
        request.setAttribute("orderStatuses",OrderStatus.values());
        try {
            LocalDate start=date(from),end=date(to);
            if(start!=null&&end!=null&&start.isAfter(end)) throw new IllegalArgumentException("開始日は終了日以前の日付を選んでください。");
            int page=1; try { page=Integer.parseInt(value(request,"page")); } catch(NumberFormatException ignored) { }
            request.setAttribute("dashboard",dashboard.findDashboard(start,end,page,today));
        } catch(IllegalArgumentException e) {
            response.setStatus(400); request.setAttribute("dashboardError",e.getMessage());
        } catch(SQLException|IOException|ClassNotFoundException e) {
            getServletContext().log("Administrator dashboard could not be retrieved",e);
            response.setStatus(503); request.setAttribute("dashboardError","集計情報を取得できませんでした。少し時間をおいて、もう一度お試しください。");
        }
        request.getRequestDispatcher("/WEB-INF/views/admin-dashboard.jsp").forward(request,response);
    }
    private static String value(HttpServletRequest request,String name) { String value=request.getParameter(name); return value==null?"":value.trim(); }
    private static LocalDate date(String value) {
        if(value.isEmpty()) return null;
        try {
            if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("Invalid date",value,0);
            LocalDate result=LocalDate.parse(value); if(result.getYear()<1) throw new DateTimeParseException("Invalid year",value,0); return result;
        } catch(DateTimeParseException e) { throw new IllegalArgumentException("日付は有効な年月日（YYYY-MM-DD）で入力してください。"); }
    }
}
