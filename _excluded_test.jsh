import java.sql.*;
Connection c = DriverManager.getConnection("jdbc:postgresql://121.37.186.131:19995/test?user=sqlbuilder1&password=huawei@123&prepareThreshold=0");
Statement s = c.createStatement();
try { s.execute("drop table t_ex_test"); } catch (Exception e) { System.out.println("drop ignored"); }
s.execute("create table t_ex_test(id int primary key, val int)");
s.execute("insert into t_ex_test values(1,1)");
String[] sqls = {
    "insert into t_ex_test values(1,2) as new on duplicate key update val=new.val",
    "insert into t_ex_test values(1,2) as excluded(id,val) on duplicate key update val=excluded.val",
    "insert into t_ex_test values(1,2) on duplicate key update val=values(val)",
    "insert into t_ex_test values(1,2) on duplicate key update val=t_ex_test.val"
};
for (String sql : sqls) {
    try {
        int n = s.executeUpdate(sql);
        ResultSet rs = s.executeQuery("select val from t_ex_test where id=1");
        rs.next();
        System.out.println("OK affected=" + n + " val=" + rs.getInt(1) + " :: " + sql);
    } catch (Exception e) {
        System.out.println("FAIL: " + e.getMessage().split("\n")[0] + " :: " + sql);
    }
}
c.close();
/exit
