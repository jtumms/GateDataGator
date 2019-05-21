package com.tummsmedia;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.h2.tools.Server;


import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static final String URL_TO_MAINGATE_ACCESS_LOGS = "jdbc:ucanaccess:///file-path/AccessDb.mdb;memory=true";
    public static final String URL_TO_BACKGATE_ACCESS_LOGS = "jdbc:ucanaccess:///file-path/AccessDb.mdb;memory=true";
    AccessManager accessManagerMain;
    AccessManager accessManagerBack;
    private static final String url = "jdbc:postgresql://localhost/data";
    private static final String user = "postgres";
    private static final String password = "********";
    private static String mainTimestamp;
    private static String backTimestamp;

    {
        try {
            accessManagerMain = new AccessManager(URL_TO_MAINGATE_ACCESS_LOGS);
            accessManagerBack = new AccessManager(URL_TO_BACKGATE_ACCESS_LOGS);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Server.createWebServer().start();


        final Connection conn = DriverManager.getConnection("jdbc:h2:./main");

        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS gate_data (id IDENTITY, gate_name VARCHAR, mod_date_time VARCHAR, record_count INT)");

        Thread statusCheckThread = new Thread(new Runnable() {
            @Override
            public void run() {

                FTPClient ftp = new FTPClient();
                final String USERNAME = "*****";
                final String PASSWORD = "*****";
                final String MAIN_GATE_IP = "0.0.0.0";
                final String BACK_GATE_IP = "0.0.0.0";
                final int PORT = 21;
                final String[] addresses = {MAIN_GATE_IP,BACK_GATE_IP};


                while (true){
                    try {
                        int counter = 1;
                        for (String address:addresses) {
                            ftp.connect(address, PORT);
                            ftp.login(USERNAME, PASSWORD);
                            int reply = ftp.getReplyCode();

                            if(!FTPReply.isPositiveCompletion(reply)) {
                                ftp.disconnect();
                                System.err.println("FTP server refused connection.");
                                System.exit(1);
                            }
                            String updateDateTime = convertDateTime(ftp.getModificationTime("~/data/data_file.txt"));
                            String gName = (address.equals(MAIN_GATE_IP) ? "MAINGATE" : "BACKGATE" );
                            GateStatusObject gateStatusObject = new GateStatusObject();
                            gateStatusObject.setId(counter);
                            gateStatusObject.setGateName(gName);
                            gateStatusObject.setModTime(updateDateTime);
                            updateGateStatus(gateStatusObject, conn);


                            if (ftp.isConnected()) {
                                ftp.logout();
                                ftp.disconnect();
                            }
                            counter++;

                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    try {
                        Thread.sleep(1800000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


            }
        });
        statusCheckThread.start();

        Runnable accessLogService = new Runnable() {
            public void run() {
                try {
                    getGateLogs(URL_TO_MAINGATE_ACCESS_LOGS);
                } catch (SQLException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                try {
                    getGateLogs(URL_TO_BACKGATE_ACCESS_LOGS);
                } catch (SQLException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }

            }
        };

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(accessLogService, 0, 24, TimeUnit.HOURS);

    }
    static String convertDateTime(String time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String adjustedDateTime = "";
        try {
            dateFormat.setTimeZone(TimeZone.getTimeZone("America/New York"));
            adjustedDateTime = dateFormat.parse(time).toString();


        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        return adjustedDateTime;
    }
    static void updateGateStatus(GateStatusObject gateStatusObject, Connection conn) throws SQLException {

        PreparedStatement stmt1 = conn.prepareStatement("MERGE INTO gate_data KEY (ID) VALUES (?, ?, ?, NULL)");
        stmt1.setLong(1, gateStatusObject.getId());
        stmt1.setString(2, gateStatusObject.getGateName());
        stmt1.setString(3, gateStatusObject.getModTime());
        stmt1.execute();
    }

    static void getGateLogs(String urlToAccessDb) throws SQLException, ParseException {
        final Connection connPostgres = connectToMainDb(url, user, password);
        PreparedStatement getMainTimeStamp = connPostgres.prepareStatement("SELECT MAX(timestamp) FROM resident_gate_access WHERE reader = 'Maingate'");
        PreparedStatement getBackTimeStamp = connPostgres.prepareStatement("SELECT MAX(timestamp) FROM resident_gate_access WHERE reader = 'Backgate'");
        ResultSet rsMain = getMainTimeStamp.executeQuery();
        ResultSet rsBack = getBackTimeStamp.executeQuery();
        while (rsMain.next()) {
            mainTimestamp = rsMain.getString("max");
        }
        while (rsBack.next()){
            backTimestamp = rsBack.getString("max");
        }
        String[] partsMain = mainTimestamp.split(" ");
        String maxDateMain = partsMain[0];
        String maxTimeMain = partsMain[1];
        String[] partsBack = backTimestamp.split(" ");
        String maxDateBack = partsBack[0];
        String maxTimeBack = partsBack[1];
        String maxDateTime;
        String workingTimestamp;
        if (urlToAccessDb.equals(URL_TO_MAINGATE_ACCESS_LOGS)){

            maxDateTime = maxDateMain;
            workingTimestamp = mainTimestamp;
        }
        else{
            maxDateTime = maxDateBack;
            workingTimestamp = backTimestamp;
        }
        java.sql.Timestamp tsMaster = java.sql.Timestamp.valueOf(workingTimestamp);


        Connection accessConn = DriverManager.getConnection(urlToAccessDb);
        String query = "SELECT * FROM accessLog WHERE logDate>=?";
        PreparedStatement stmt2 = accessConn.prepareStatement(query);
        stmt2.setString(1, maxDateTime + " 00:00:00.000000");


        final ResultSet resultSet = stmt2.executeQuery();
        ResultSetMetaData rsmd = resultSet.getMetaData();
        final int colNumber = rsmd.getColumnCount();

        while (resultSet.next()) {
            String updatePostgres = "INSERT INTO resident_gate_access(\"ID\", \"LogDate\", \"LogTime\", \"FacilityCode\", \"Code\", \"LastName\", \"FirstName\", \"Unit\", reader, \"Verified\", \"In\", alarm, \"Tag\", \"Notes\", timestamp) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            ArrayList<String> data = new ArrayList<String>();
            for (int i = 1; i <= colNumber; i++) {
                String columnValue = resultSet.getString(i);
                data.add(columnValue);
            }
            PreparedStatement insert = connPostgres.prepareStatement(updatePostgres);
            insert.setInt(1, Integer.parseInt(data.get(0)));
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = sdf1.parse(data.get(1));
            java.sql.Date sqlDate = new java.sql.Date(date.getTime());
            insert.setDate(2, sqlDate);
            long hms = sdf1.parse(data.get(2)).getTime();
            Time time = new Time(hms);
            insert.setTime(3, time);
            insert.setString(4, data.get(3));
            insert.setString(5, data.get(4));
            insert.setString(6, data.get(5));
            insert.setString(7, data.get(6));
            insert.setString(8, data.get(7));
            insert.setString(9, data.get(8));
            insert.setString(10, data.get(9));
            insert.setBoolean(11, Boolean.valueOf(data.get(10)));
            insert.setBoolean(12, Boolean.valueOf(data.get(11)));
            insert.setString(13, data.get(12));
            insert.setString(14, data.get(13));
            String datePrefix = data.get(1).substring(0, 10);
            String timeSuffix = data.get(2).substring(10);
            String dateAndTime = datePrefix + timeSuffix;
            java.sql.Timestamp ts1 = java.sql.Timestamp.valueOf(dateAndTime);
            insert.setTimestamp(15, ts1);
            if (ts1.after(tsMaster)) {
                insert.execute();
            }
            else {
                data.clear();
                continue;
            }
            data.clear();
        }
        System.out.println(String.format("Completed database update for %s", urlToAccessDb));
        accessConn.close();
        connPostgres.close();

    }

    static Connection connectToMainDb(String url, String user, String password) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected to the PostgreSQL server successfully.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }


}
