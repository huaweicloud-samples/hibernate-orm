import java.sql.*;
import java.util.Properties;
Class.forName("org.postgresql.Driver");
var url = "jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off";
var props = new Properties();
props.setProperty("user","sqlbuilder1");
props.setProperty("password","huawei@123");
try (var con = DriverManager.getConnection(url, props)) {
    System.out.println("connected");
    try (var st = con.createStatement()) {
        try { st.execute("drop table _t_like"); } catch(Exception e){}
        st.execute("create table _t_like(id int, data varchar(50))");
        // id=1 Product\one (one backslash), id=2 Product%two, id=3 Product"three
        st.execute("insert into _t_like values(1,'Product\\one')");
        st.execute("insert into _t_like values(2,'Product%two')");
        st.execute("insert into _t_like values(3,'Product\"three')");
        // verify data
        try(var rs=st.executeQuery("select id,data from _t_like order by id")){
            while(rs.next()) System.out.println("row id="+rs.getInt(1)+" data=["+rs.getString(2)+"]");
        }
        // t1: like '%\%' no escape clause (M mode MySQL default \ is escape => \% = literal % => matches id=2)
        try(var rs=st.executeQuery("select id from _t_like where data like '%\\%' order by id")){
            System.out.print("t1 no-escape: ");
            while(rs.next()) System.out.print(rs.getInt(1)+" ");
            System.out.println();
        } catch(Exception e){ System.out.println("t1 no-escape ERR: "+e.getMessage()); }
        // t2: like '%\%' escape '' (empty escape => \ is literal => matches id=1)
        try(var rs=st.executeQuery("select id from _t_like where data like '%\\%' escape '' order by id")){
            System.out.print("t2 escape-empty: ");
            while(rs.next()) System.out.print(rs.getInt(1)+" ");
            System.out.println();
        } catch(Exception e){ System.out.println("t2 escape-empty ERR: "+e.getMessage()); }
        // t3: like '%\%' escape '\' (explicit backslash escape, PG-style)
        try(var rs=st.executeQuery("select id from _t_like where data like '%\\%' escape '\\' order by id")){
            System.out.print("t3 escape-backslash: ");
            while(rs.next()) System.out.print(rs.getInt(1)+" ");
            System.out.println();
        } catch(Exception e){ System.out.println("t3 escape-backslash ERR: "+e.getMessage()); }
        // interval types
        try { st.execute("create table _t_iv(d interval second)"); System.out.println("t4 interval second OK"); st.execute("drop table _t_iv"); }
        catch(Exception e){ System.out.println("t4 interval second ERR: "+e.getMessage()); }
        try { st.execute("create table _t_iv2(d interval second(6))"); System.out.println("t5 interval second(6) OK"); st.execute("drop table _t_iv2"); }
        catch(Exception e){ System.out.println("t5 interval second(6) ERR: "+e.getMessage()); }
        try { st.execute("create table _t_iv3(d interval(6))"); System.out.println("t6 interval(6) OK"); st.execute("drop table _t_iv3"); }
        catch(Exception e){ System.out.println("t6 interval(6) ERR: "+e.getMessage()); }
        st.execute("drop table _t_like");
    }
}
System.out.println("DONE");
/exit
