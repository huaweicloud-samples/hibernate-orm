import java.sql.*;
import java.time.*;
import java.util.Properties;

Class.forName("org.postgresql.Driver");
var url = "jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off";
var props = new Properties();
props.setProperty("user", "sqlbuilder1");
props.setProperty("password", "huawei@123");
try (var con = DriverManager.getConnection(url, props)) {
    System.out.println("connected");
    try (var st = con.createStatement()) {
        try { st.execute("drop table _t_odt"); } catch (Exception e) { System.out.println("drop: " + e.getMessage()); }
    }
    try (var st = con.createStatement()) {
        st.execute("create table _t_odt (id int, name varchar(255), theInstant datetime(6), theLocalDate date, theLocalDateTime datetime(6), theLocalTime time(0), theOffsetDateTime datetime(6))");
        System.out.println("created 7-col table");
    }
    try (var ps = con.prepareStatement("insert into _t_odt (id, theLocalDateTime) values (?,?)")) {
        ps.setInt(1, 1); ps.setTimestamp(2, Timestamp.valueOf("2026-07-13 10:00:00")); ps.executeUpdate();
        System.out.println("inserted (theOffsetDateTime=null)");
    }
    try (var ps = con.prepareStatement("select id, name, theInstant, theLocalDate, theLocalDateTime, theLocalTime, theOffsetDateTime from _t_odt where id=?")) {
        ps.setInt(1, 1);
        try (var rs = ps.executeQuery()) {
            rs.next();
            var md = rs.getMetaData();
            for (int c = 1; c <= 7; c++) {
                System.out.println("col " + c + " name=" + md.getColumnName(c) + " type=" + md.getColumnTypeName(c) + " jdbcType=" + md.getColumnType(c));
            }
            try { System.out.println("col7 getObject OffsetDateTime: " + rs.getObject(7, OffsetDateTime.class)); }
            catch (Exception e) { System.out.println("col7 getObject OffsetDateTime FAILED: " + e.getMessage()); }
            try { System.out.println("col7 getTimestamp: " + rs.getTimestamp(7) + " wasNull=" + rs.wasNull()); }
            catch (Exception e) { System.out.println("col7 getTimestamp FAILED: " + e.getMessage()); }
        }
    }
    try (var st = con.createStatement()) { st.execute("drop table _t_odt"); }
}
System.out.println("DONE");
/exit
