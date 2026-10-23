package com.gamestore;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnectionTest {

    public static void main(String[] args) {

        // 여기에 JDBC 연결 코드 작성
        String url = "jdbc:postgresql://localhost:5432/playport_db";
        String user = "postgres";
        String password = System.getenv("PLAYPORT_DB_PASSWORD");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("DB connection successful");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
    }

    public static Connection getConnection(String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}