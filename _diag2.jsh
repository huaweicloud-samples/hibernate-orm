import java.sql.*;
import java.util.Properties;
Class.forName("org.postgresql.Driver");
var url = "jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off";
var props = new Properties();
props.setProperty("user","sqlbuilder1");
props.setProperty("password","huawei@123");
try (var con = DriverManager.getConnection(url, props)) {
    try (var st = con.createStatement()) {
        try { st.execute("drop table _t_p"); } catch(Exception e){}
        try { st.execute("drop table _t_c"); } catch(Exception e){}
        st.execute("create table _t_p(id int, color_id int)");
        st.execute("create table _t_c(id int, code varchar(20))");
        st.execute("insert into _t_p values(1,10)");
        st.execute("insert into _t_c values(10,'red')");
        // a: MySQL multi-table delete: delete alias from table alias join ...
        try { int r=st.executeUpdate("delete p1_0 from _t_p p1_0 join _t_c c1_0 on c1_0.id=p1_0.color_id where c1_0.code='red'"); System.out.println("a delete-alias-from OK r="+r); }
        catch(Exception e){ System.out.println("a delete-alias-from ERR: "+e.getMessage().split("\n")[0]); }
        // restore
        st.execute("insert into _t_p values(1,10)");
        // b: derived table from (values(0))
        try { var rs=st.executeQuery("select 1 from (values(0)) d_"); rs.next(); System.out.println("b values-derived OK: "+rs.getInt(1)); }
        catch(Exception e){ System.out.println("b values-derived ERR: "+e.getMessage().split("\n")[0]); }
        // c: exists subquery with (values(0)) joined
        try { var rs=st.executeQuery("select id from _t_p p1_0 where exists(select 1 from (values(0)) d_ join _t_c c1_0 on c1_0.id=p1_0.color_id where c1_0.code='red')"); System.out.print("c exists-values-join OK: "); while(rs.next()) System.out.print(rs.getInt(1)+" "); System.out.println(); }
        catch(Exception e){ System.out.println("c exists-values-join ERR: "+e.getMessage().split("\n")[0]); }
        st.execute("drop table _t_p"); st.execute("drop table _t_c");
    }
}
System.out.println("DONE2");
/exit
