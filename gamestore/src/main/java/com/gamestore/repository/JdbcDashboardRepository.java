package com.gamestore.repository;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.gamestore.model.*;
import com.gamestore.util.DBConnection;

/** Administrator-only aggregates. Every query uses the order's creation date and one read snapshot. */
public class JdbcDashboardRepository {
    public static final int PAGE_SIZE = 10;
    @FunctionalInterface interface ConnectionProvider {
        Connection open() throws SQLException, IOException, ClassNotFoundException;
    }
    private final ConnectionProvider connections;
    public JdbcDashboardRepository() { this(DBConnection::getConnection); }
    JdbcDashboardRepository(ConnectionProvider connections) { this.connections = connections; }

    public AdminDashboard findDashboard(LocalDate from, LocalDate to, int requestedPage, LocalDate today)
            throws SQLException, IOException, ClassNotFoundException {
        Objects.requireNonNull(today);
        if (from != null && to != null && from.isAfter(to)) { throw new IllegalArgumentException("Invalid date range"); }
        String where = " WHERE TRUE";
        List<LocalDateTime> parameters = new ArrayList<>();
        if (from != null) { where += " AND o.created_at >= ?"; parameters.add(from.atStartOfDay()); }
        if (to != null) { where += " AND o.created_at < ?"; parameters.add(to.plusDays(1).atStartOfDay()); }
        LocalDate chartTo = to != null ? to : (from != null && from.isAfter(today) ? from : today);
        LocalDate chartFrom = chartTo.minusDays(13);
        if (chartFrom.isBefore(LocalDate.of(1,1,1))) { chartFrom = LocalDate.of(1,1,1); }
        if (from != null && from.isAfter(chartFrom)) { chartFrom = from; }
        try (Connection conn = connections.open()) {
            conn.setReadOnly(true); conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); conn.setAutoCommit(false);
            try {
                long units;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM orders o JOIN order_items i ON i.order_id=o.order_id" + where + " AND o.status='COMPLETED'")) {
                    bind(stmt, parameters); try (ResultSet rows = stmt.executeQuery()) { rows.next(); units = rows.getLong(1); }
                }
                DashboardSummary summary;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FILTER (WHERE o.status='COMPLETED'), "
                        + "COALESCE(SUM(o.total_price) FILTER (WHERE o.status='COMPLETED'),0), COUNT(DISTINCT o.user_id) FILTER (WHERE o.status='COMPLETED'), "
                        + "COUNT(*) FILTER (WHERE o.status='PENDING'), COALESCE(SUM(o.total_price) FILTER (WHERE o.status='PENDING'),0), "
                        + "COUNT(*) FILTER (WHERE o.status='CANCELLED'), COALESCE(SUM(o.total_price) FILTER (WHERE o.status='CANCELLED'),0) FROM orders o" + where)) {
                    bind(stmt, parameters); try (ResultSet rows = stmt.executeQuery()) {
                        rows.next(); summary = new DashboardSummary(rows.getLong(1), rows.getLong(2), units, rows.getLong(3), rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getLong(7));
                    }
                }
                int count;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM games"); ResultSet rows = stmt.executeQuery()) { rows.next(); count = rows.getInt(1); }
                int page = Math.max(1, Math.min(requestedPage, Math.max(1, Math.ceilDiv(count, PAGE_SIZE))));
                List<GameSales> games = new ArrayList<>();
                String gameSql = "SELECT g.game_id,g.title,g.price,g.genre,g.image_url,g.is_active,COALESCE(s.units,0) AS units,COALESCE(s.buyers,0) AS buyers,COALESCE(s.amount,0) AS amount "
                        + "FROM games g LEFT JOIN (SELECT i.game_id,COUNT(*) AS units,COUNT(DISTINCT o.user_id) AS buyers,SUM(i.price) AS amount "
                        + "FROM orders o JOIN order_items i ON i.order_id=o.order_id" + where + " AND o.status='COMPLETED' GROUP BY i.game_id) s ON s.game_id=g.game_id "
                        + "ORDER BY COALESCE(s.amount,0) DESC,COALESCE(s.units,0) DESC,g.game_id DESC LIMIT ? OFFSET ?";
                try (PreparedStatement stmt = conn.prepareStatement(gameSql)) {
                    bind(stmt, parameters); stmt.setInt(parameters.size()+1, PAGE_SIZE); stmt.setLong(parameters.size()+2, (long)(page-1)*PAGE_SIZE);
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) {
                            Game game = new Game(rows.getString("game_id"),rows.getString("title"),rows.getInt("price"),rows.getString("genre"),rows.getString("image_url"),null,rows.getBoolean("is_active"));
                            games.add(new GameSales(game,rows.getLong("units"),rows.getLong("buyers"),rows.getLong("amount")));
                        }
                    }
                }
                Map<LocalDate, DailySales> totals = new HashMap<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT CAST(o.created_at AS DATE),COUNT(*),SUM(o.total_price) FROM orders o" + where
                        + " AND o.status='COMPLETED' AND o.created_at>=? AND o.created_at<? GROUP BY CAST(o.created_at AS DATE) ORDER BY 1")) {
                    bind(stmt, parameters); stmt.setObject(parameters.size()+1,chartFrom.atStartOfDay()); stmt.setObject(parameters.size()+2,chartTo.plusDays(1).atStartOfDay());
                    try (ResultSet rows = stmt.executeQuery()) {
                        while (rows.next()) { LocalDate date=rows.getObject(1,LocalDate.class); totals.put(date,new DailySales(date,rows.getLong(2),rows.getLong(3),0)); }
                    }
                }
                long peak = totals.values().stream().mapToLong(DailySales::amount).max().orElse(0);
                List<DailySales> days = new ArrayList<>();
                for (LocalDate date=chartFrom;!date.isAfter(chartTo);date=date.plusDays(1)) {
                    DailySales day=totals.getOrDefault(date,new DailySales(date,0,0,0));
                    int percent=peak==0||day.amount()==0 ? 0 : Math.max(1,(int)Math.round(day.amount()*100.0/peak));
                    days.add(new DailySales(date,day.orderCount(),day.amount(),percent));
                }
                conn.commit(); return new AdminDashboard(summary,days,games,count,page,PAGE_SIZE);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
    private static void bind(PreparedStatement stmt,List<LocalDateTime> parameters) throws SQLException {
        for(int i=0;i<parameters.size();i++) stmt.setObject(i+1,parameters.get(i));
    }
}
