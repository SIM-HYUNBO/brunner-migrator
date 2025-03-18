package com.brunner.db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.fasterxml.jackson.databind.JsonNode;

public class DBConnector {
    public static Connection getDBConnection(JsonNode dbConfig) throws SQLException {
        String dbType = dbConfig.get("type").asText().toLowerCase();
        String host = dbConfig.get("host").asText();
        int port = dbConfig.get("port").asInt();
        String databaseName = dbConfig.get("databaseName").asText();
        String username = dbConfig.get("username").asText();
        String password = dbConfig.get("password").asText();

        String url;
        switch (dbType) {
            case "mysql" -> url = "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?useSSL=false&serverTimezone=UTC";
            case "postgresql" -> url = "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
            case "oracle" -> {
                try {
                    Class.forName("oracle.jdbc.driver.OracleDriver");
                } catch (ClassNotFoundException e) {
                    throw new SQLException("Oracle JDBC Driver not found.", e);
                }
                url = "jdbc:oracle:thin:@" + host + ":" + port + ":" + databaseName;
            }
            case "mssql" -> url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + databaseName;
            default -> throw new SQLException("Not supported DB Type: " + dbType);
        }

        return DriverManager.getConnection(url, username, password);
    }
}