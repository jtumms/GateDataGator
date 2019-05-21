package com.tummsmedia;

import java.sql.*;

public class AccessManager {

    private Connection conn;

    public AccessManager(String dbURL) throws SQLException {
        conn = DriverManager.getConnection(dbURL);

    }
    public ResultSet query(String sqlString) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet resultSet = stmt.executeQuery(sqlString);
        return resultSet;
    }
    public int update(String sqlString) throws SQLException {
        Statement stmt = conn.createStatement();
        int complete = stmt.executeUpdate(sqlString);
        return complete;
    }

    public int updateReturnID(String sqlString)throws SQLException{
        Statement stmt = conn.createStatement();
        int id = -1;
        stmt.executeUpdate(sqlString, Statement.RETURN_GENERATED_KEYS);
        ResultSet resultSet = stmt.getGeneratedKeys();
        if (resultSet.next()){
            id = resultSet.getInt(1);
        }
        return id;

    }

}
