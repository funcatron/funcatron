package funcatron.helpers;

import clojure.java.api.Clojure;

import java.util.List;

/**
 * A 2 element Tuple
 */
public class Tuple2<A, B> {

    private final A a;
    private final B b;

    public Tuple2(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public A _1() {return a;}
    public B _2() {return b;}
    public A a() {return a;}
    public B b() {return b;}

    /**
     * Convert the Tuple into a List
     * @return a List containing the elements of the Tuple
     */
    public List toList() {
        return (List) Clojure.var("clojure/core", "list").invoke(a, b);
    }
}
