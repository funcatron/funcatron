package funcatron.java_spring_sample;


/**
 * Look... it's a PoJo
 */
public class Greeting {

    private final long id;
    private final String content;
private final String time;
    public Greeting(long id, String content) {
        this.id = id;
        this.time = content + id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public String getTime() {return time;}

    public String getContent() {
        return content;
    }
}
