package funcatron.jvm_services.clojure;

import funcatron.intf.ClassloaderProvider;

import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import clojure.lang.RT;

/**
 * Initialize the Clojure Context
 */
public class InitRt implements ClassloaderProvider {
    /**
     * Given a base classloader (or one vended from another ClassloaderProvider), return one
     *
     * @param cl           the current classlaoder
     * @param addEndOfLife Add a function at the end of life. If there's anything allocated by the installation of the
     *                     operation (like creating a JDBC pool), then the operation should be released by the
     *                     end of life function.
     * @param logger       the logger
     * @return a new classlaoder
     */
    @Override
    public ClassLoader buildFrom(ClassLoader cl, Function<Function<Logger, Void>, Void> addEndOfLife, Logger logger) {
        // load the Clojure runtime
        RT.box(33);
        return cl;
    }
}
