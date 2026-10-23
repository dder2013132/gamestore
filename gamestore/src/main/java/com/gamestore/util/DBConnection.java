package com.gamestore.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DBConnection {

    public static Connection getConnection()
            throws SQLException, IOException {

        // 다음 미션에서 db.properties 읽기
        Properties props = new Properties();
        try (FileInputStream fis =
            new FileInputStream("gamestore/db.properties")) {
                props.load(fis);
            }
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String password = props.getProperty("db.password");

        return DriverManager.getConnection(url, user, password);
    }
}