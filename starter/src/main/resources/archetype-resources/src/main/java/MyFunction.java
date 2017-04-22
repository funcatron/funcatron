package $package;

import funcatron.intf.Func;
import funcatron.intf.Context;
import funcatron.devshim.Register;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;
import java.util.Date;
import redis.clients.jedis.Jedis;

/**
 * Hello world!
 */
public class MyFunction implements Func<MyPojo> {
    /**
     * Implement your business logic here
     *
     * @param pojo    the incoming request parameters, converted by Jackson to your class
     * @param context the context of the request
     * @return the result
     */
    @Override
    public Object apply(MyPojo pojo, final Context context) throws Exception {
        if (null == pojo) {
            pojo = new MyPojo();
            pojo.setName("Example");
            pojo.setAge(42);
        }

        // if we have a Redis driver, let the world know
        context.vendForName("cache", Jedis.class).map(a ->
        {
            context.getLogger().log(Level.INFO, "Yay!. Got Redis Driver");
            return null;
        });

        pojo.setName("Hello: " + pojo.getName() + " at " + (new Date()));
        pojo.setAge(pojo.getAge() + 1);
        // put the pojo in the DB
        addToDatabase(pojo, context);
        return pojo;
    }

    /**
     * Add the pojo to the database
     *
     * @param pojo the Pojo to add
     * @param c    the context
     */
    private void addToDatabase(MyPojo pojo, Context c) {
        try {
            // get the DB connection
            c.vendForName("db", Connection.class).
                    map((Connection db) -> {
                        try {
                            // create the table if it doesn't exist
                            Statement ct = db.createStatement();
                            ct.execute("CREATE TABLE IF NOT EXISTS pojos(NAME VARCHAR(8192), AGE BIGINT);");

                            // insert the pojo
                            PreparedStatement st = db.prepareStatement("INSERT INTO pojos (name, age) VALUES (?, ?)");
                            st.setString(1, pojo.getName());
                            st.setInt(2, pojo.getAge());
                            st.executeUpdate();
                            st.close();

                            // get the pojo count and display it
                            ResultSet rs = ct.executeQuery("SELECT COUNT(*) FROM pojos");
                            if (rs.next()) {
                                Object cnt = rs.getObject(1);
                                c.getLogger().log(Level.INFO, "There are " + cnt + " rows in the DB");
                            }
                            rs.close();
                            ct.close();
                        } catch (SQLException se) {
                            c.getLogger().log(Level.WARNING, "Failed to insert pojo", se);
                        }
                        return null;
                    });
        } catch (Exception e) {
            c.getLogger().log(Level.WARNING, "Failed to add pojo to db", e);
        }
    }


    private static String funcatronDevHost() {
        return "localhost";
    }

    private static int funcatronDevPort() {
        return 54657;
    }

    /**
     * Call this method during development, but not production, to
     * hook up to the Funcatron
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        System.out.println("Starting connection to Funcatron dev server");
        System.out.println("run the Funcatron dev server with: docker run -ti --rm  -e TRON_1=--devmode -p 3000:3000 -p 54657:54657 funcatron/tron:v0.2.1");
        System.out.println("Then point your browser to http://localhost:3000/api/sample");

        Register.register(funcatronDevHost(), funcatronDevPort(),
                new File("src/main/resources/funcatron.yaml"),
                new File("src/main/resources/exec_props.json"));
    }
}
