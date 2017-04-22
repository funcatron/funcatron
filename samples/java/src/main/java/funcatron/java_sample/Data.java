package funcatron.java_sample;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    public Data(@JsonProperty("name") String name,
                @JsonProperty("age") int age) {
        this.name = name;
        this.age = age;
    }
}
