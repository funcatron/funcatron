package funcatron.helpers;

import clojure.java.api.Clojure;

import java.util.List;

/**
 * A 3 element tuple
 */
public class Tuple3<A, B, C> {

    private final A a;
    private final B b;
    private final C c;

    public Tuple3(A a, B b,C c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public A _1() {return a;}
    public B _2() {return b;}
    public C _3() {return c;}
    public A a() {return a;}
    public B b() {return b;}
    public C c() {return c;}

    /**
     * Convert the Tuple into a List
     * @return a List containing the elements of the Tuple
     */
    public List toList() {
        return (List) Clojure.var("clojure/core", "list").invoke(a, b, c);
    }
}
