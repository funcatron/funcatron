package funcatron.java_sample;

/**
 * Created by dpp on 12/2/16.
 */
public class Data {
    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    private final String name;
    private final int age;

    public Data(String name, int age) {
        this.name = name;
        this.age = age;
    }
}
