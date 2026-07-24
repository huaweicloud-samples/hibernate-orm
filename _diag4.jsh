import java.sql.*;
import java.util.Properties;
Class.forName("org.postgresql.Driver");
var url = "jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off";
var props = new Properties();
props.setProperty("user","sqlbuilder1");
props.setProperty("password","huawei@123");
try (var con = DriverManager.getConnection(url, props)) {
    try (var st = con.createStatement()) {
        // check collation
        try(var rs=st.executeQuery("show server_encoding")){rs.next();System.out.println("server_encoding="+rs.getString(1));}
        catch(Exception e){System.out.println("server_encoding ERR "+e.getMessage().split("\n")[0]);}
        try(var rs=st.executeQuery("select datcompatibility from pg_database where datname='test'")){rs.next();System.out.println("datcompatibility="+rs.getString(1));}
        catch(Exception e){System.out.println("datcompat ERR "+e.getMessage().split("\n")[0]);}
        // cs test: use COLLATE to force case-sensitive
        try { st.execute("drop table _t_l"); } catch(Exception e){}
        st.execute("create table _t_l(id int, data varchar(30))");
        String[] rows={"Product_one","proDUct two","Product three","pROducT four","Product five","Prodact six","prodACt seven","Prod_act eight","prod_ACt nine"};
        for(int i=0;i<rows.length;i++) st.execute("insert into _t_l values("+(i+1)+",'"+rows[i]+"')");
        // force case-sensitive via COLLATE
        try(var rs=st.executeQuery("select id from _t_l where data like 'Prod%' COLLATE \"C\" order by id")){System.out.print("like Prod% COLLATE C: ");while(rs.next())System.out.print(rs.getInt(1)+" ");System.out.println();}
        catch(Exception e){System.out.println("COLLATE C ERR: "+e.getMessage().split("\n")[0]);}
        try(var rs=st.executeQuery("select id from _t_l where data like 'Prod%' COLLATE \"default\" order by id")){System.out.print("like Prod% COLLATE default: ");while(rs.next())System.out.print(rs.getInt(1)+" ");System.out.println();}
        catch(Exception e){System.out.println("COLLATE default ERR: "+e.getMessage().split("\n")[0]);}
        // check actual column collation of _t_l
        try(var rs=st.executeQuery("select collation_name from information_schema.columns where table_name='_t_l' and column_name='data'")){if(rs.next())System.out.println("_t_l.data collation="+rs.getString(1));else System.out.println("_t_l.data collation=NULL");}
        catch(Exception e){System.out.println("collation query ERR: "+e.getMessage().split("\n")[0]);}
        st.execute("drop table _t_l");
    }
}
System.out.println("DONE4");
/exit
