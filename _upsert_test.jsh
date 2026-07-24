import java.sql.*;
Connection c = DriverManager.getConnection("jdbc:postgresql://121.37.186.131:19995/test?user=sqlbuilder1&password=huawei@123&prepareThreshold=0");
Statement s = c.createStatement();
try { s.execute("drop table t_up_test"); } catch (Exception e) { System.out.println("drop ignored"); }
s.execute("create table t_up_test(id int primary key, val int)");
s.execute("insert into t_up_test values(1,1)");
System.out.println("insert(1,1) OK");
// test 1: ON DUPLICATE KEY UPDATE col=col (MySQL-standard do-nothing)
try {
    int n = s.executeUpdate("insert into t_up_test values(1,2) on duplicate key update val=val");
    System.out.println("ON DUPLICATE KEY UPDATE val=val: OK affected=" + n);
} catch (Exception e) { System.out.println("FAIL col=col: " + e.getMessage()); }
ResultSet rs = s.executeQuery("select val from t_up_test where id=1");
rs.next();
System.out.println("val after upsert=" + rs.getInt(1) + " (expect 1, unchanged)");
// test 2: INSERT IGNORE
try {
    int n = s.executeUpdate("insert ignore into t_up_test values(1,3)");
    System.out.println("INSERT IGNORE: OK affected=" + n);
    ResultSet rs2 = s.executeQuery("select val from t_up_test where id=1");
    rs2.next();
    System.out.println("val after ignore=" + rs2.getInt(1) + " (expect 1, unchanged)");
} catch (Exception e) { System.out.println("FAIL insert ignore: " + e.getMessage()); }
c.close();
/exit
