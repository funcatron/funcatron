package funcatron.helpers;

import clojure.java.api.Clojure;

import java.util.List;

/**
 * A 3 element tuple
 */
public class Tuple3<A, B, C> extends Tuple2<A,B> {

    private final C c;

    public Tuple3(A a, B b,C c) {
        super(a,b);
        this.c = c;
    }

    public C _3() {return c;}
    public C c() {return c;}

    /**
     * Convert the Tuple into a List
     * @return a List containing the elements of the Tuple
     */
    @Override
    public List toList() {
        return (List) Clojure.var("clojure/core", "list").invoke(_1(), _2(), c);
    }
}
