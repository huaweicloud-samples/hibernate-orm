import java.sql.*;
Class.forName("org.postgresql.Driver");
Connection c = DriverManager.getConnection("jdbc:postgresql://121.37.186.131:19995/test?preparedStatementCacheQueries=0&batchMode=off","sqlbuilder1","huawei@123");
Statement s = c.createStatement();
try { s.execute("drop table if exists TestEntity cascade"); } catch(Exception e){System.out.println("DROP_ERR "+e);}
try {
    s.execute("create table TestEntity (id integer not null, duration_interval numeric(10,6), primary key (id))");
    System.out.println("CREATE_OK");
} catch(Exception e){System.out.println("CREATE_ERR "+e);}
// direct select by mixed-case name
try { s.executeQuery("select * from TestEntity"); System.out.println("SEL_MIXED_OK"); } catch(Exception e){System.out.println("SEL_MIXED_ERR "+e.getMessage());}
try { s.executeQuery("select * from testentity"); System.out.println("SEL_LOWER_OK"); } catch(Exception e){System.out.println("SEL_LOWER_ERR "+e.getMessage());}
// information_schema.tables lookup (case-sensitive as Hibernate does)
ResultSet rs = s.executeQuery("select table_name from information_schema.tables where table_name='TestEntity'");
boolean found1=false; while(rs.next()){found1=true; System.out.println("ISTBL_MIXED="+rs.getString(1));} rs.close();
if(!found1) System.out.println("ISTBL_MIXED=NONE");
rs = s.executeQuery("select table_name from information_schema.tables where lower(table_name)='testentity'");
while(rs.next()){System.out.println("ISTBL_LOWER="+rs.getString(1));} rs.close();
// JDBC DatabaseMetaData.getTables (what validator uses)
DatabaseMetaData md = c.getMetaData();
rs = md.getTables(null, null, "TestEntity", new String[]{"TABLE"});
while(rs.next()){System.out.println("MDTBL="+rs.getString("TABLE_NAME"));} rs.close();
rs = md.getTables(null, null, "testentity", new String[]{"TABLE"});
while(rs.next()){System.out.println("MDTBL_LOWER="+rs.getString("TABLE_NAME"));} rs.close();
try { s.execute("drop table if exists TestEntity cascade"); } catch(Exception e){}
c.close();
System.out.println("DONE6");
/exit
