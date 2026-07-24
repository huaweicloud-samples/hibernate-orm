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
        st.execute("create table _t_l(id int, data varchar(30))");
        String[] rows={"Product_one","proDUct two","Product three","pROducT four","Product five","Prodact six","prodACt seven","Prod_act eight","prod_ACt nine"};
        for(int i=0;i<rows.length;i++) st.execute("insert into _t_l values("+(i+1)+",'"+rows[i]+"')");
        String[] qs={
            "like 'Prod%' no escape",
            "like 'Prod%' escape '$'",
            "like 'Prod%' escape ''",
            "like 'Pr%$_%' escape '$'",
            "like 'Pr%$_%' no escape",
            "not like 'Prod%' escape '$'",
            "lower(data) like lower('Prod%') escape '$'"
        };
        String[] sqls={
            "select id from _t_l where data like 'Prod%' order by id",
            "select id from _t_l where data like 'Prod%' escape '$' order by id",
            "select id from _t_l where data like 'Prod%' escape '' order by id",
            "select id from _t_l where data like 'Pr%$_%' escape '$' order by id",
            "select id from _t_l where data like 'Pr%$_%' order by id",
            "select id from _t_l where data not like 'Prod%' escape '$' order by id",
            "select id from _t_l where lower(data) like lower('Prod%') escape '$' order by id"
        };
        for(int i=0;i<qs.length;i++){
            System.out.print(qs[i]+": ");
            try(var rs=st.executeQuery(sqls[i])){ int c=0; while(rs.next()){System.out.print(rs.getInt(1)+" ");c++;} System.out.println("("+c+")"); }
            catch(Exception e){ System.out.println("ERR: "+e.getMessage().split("\n")[0]); }
        }
        st.execute("drop table _t_l");
    }
}
System.out.println("DONE3");
/exit
