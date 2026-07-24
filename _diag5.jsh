import java.sql.*;
import java.util.Properties;
Class.forName("org.postgresql.Driver");
var url = "jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off";
var props = new Properties();
props.setProperty("user","sqlbuilder1");
props.setProperty("password","huawei@123");
try (var con = DriverManager.getConnection(url, props)) {
    try (var st = con.createStatement()) {
        try { st.execute("drop table _t_l"); } catch(Exception e){}
        st.execute("create table _t_l(id int, data varchar(50))");
        st.execute("insert into _t_l values(1,'Product\\one')");
        st.execute("insert into _t_l values(2,'Product%two')");
        st.execute("insert into _t_l values(3,'Product\"three')");
        // PreparedStatement: like '%\%' escape ''  (Hibernate no-escape M mode forced)
        try(var ps=con.prepareStatement("select id from _t_l where data like ? escape '' order by id")){ ps.setString(1,"%\%"); var rs=ps.executeQuery(); System.out.print("ps like %\% escape '': "); while(rs.next())System.out.print(rs.getInt(1)+" "); System.out.println(); }
        catch(Exception e){ System.out.println("ps escape '' ERR: "+e.getMessage().split("\n")[0]); }
        // PreparedStatement: like '%\%' no escape
        try(var ps=con.prepareStatement("select id from _t_l where data like ? order by id")){ ps.setString(1,"%\%"); var rs=ps.executeQuery(); System.out.print("ps like %\% no-escape: "); while(rs.next())System.out.print(rs.getInt(1)+" "); System.out.println(); }
        catch(Exception e){ System.out.println("ps no-escape ERR: "+e.getMessage().split("\n")[0]); }
        // PreparedStatement: like '%#%' escape '#' (explicit)
        try(var ps=con.prepareStatement("select id from _t_l where data like ? escape '#' order by id")){ ps.setString(1,"%#%%"); var rs=ps.executeQuery(); System.out.print("ps like %#% escape #: "); while(rs.next())System.out.print(rs.getInt(1)+" "); System.out.println(); }
        catch(Exception e){ System.out.println("ps escape # ERR: "+e.getMessage().split("\n")[0]); }
        // literal: like '%#%' escape '#'
        try(var rs=st.executeQuery("select id from _t_l where data like '%#%' escape '#' order by id")){ System.out.print("lit like %#% escape #: "); while(rs.next())System.out.print(rs.getInt(1)+" "); System.out.println(); }
        catch(Exception e){ System.out.println("lit escape # ERR: "+e.getMessage().split("\n")[0]); }
        st.execute("drop table _t_l");
    }
}
System.out.println("DONE5");
/exit
