package com.gamestore.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    public static Connection getConnection()
            throws SQLException, IOException, ClassNotFoundException {

        // Use an external path in Tomcat; retain the local development fallback.
        Properties props = new Properties();
        String configPath = System.getenv("GAMESTORE_DB_CONFIG");
        if (configPath != null && !configPath.isBlank()) {
            try (InputStream input = new FileInputStream(configPath)) {
                props.load(input);
            }
        } else {
            try (InputStream input = new FileInputStream("db.properties")) {
                props.load(input);
            }
        }
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}
