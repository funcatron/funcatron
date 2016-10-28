package $package;

import funcatron.intf.Func;
import funcatron.intf.Context;
import funcatron.devshim.Register;
import java.io.File;

/**
 * Hello world!
 *
 */
public class MyFunction implements Func<MyPojo, MyPojo>
{
    /**
     * Implement your business logic here
     * @param pojo the incoming request parameters, converted by Jackson to your class
     * @param context the context of the request
     * @return the result
     */
    @Override
    public MyPojo apply(MyPojo pojo, Context context) {
        if (null == pojo) {
            pojo = new MyPojo();
            pojo.setName("Example");
            pojo.setAge(42);
        }

        pojo.setName("Hello: "+pojo.getName());
        pojo.setAge(pojo.getAge() + 1);
        return pojo;
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
     * @param args
     * @throws Exception
     */
    public static void main( String[] args ) throws Exception
    {
        Register.register("localhost", 54657, new File("src/main/resources/funcatron.yaml"));
    }
}
