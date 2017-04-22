package funcatron.intf;

import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A service that will take a classloader and layer another
 * classloader on top of it. This is useful for stuff like Spring
 * where they have magic classloaders.
 */
public interface ClassloaderProvider extends OrderedProvider {
    /**
     * Given a base classloader (or one vended from another ClassloaderProvider), return one
     * @param cl the current classlaoder
     * @param addEndOfLife Add a function at the end of life. If there's anything allocated by the installation of the
     *                     operation (like creating a JDBC pool), then the operation should be released by the
     *                     end of life function.
     * @param logger the logger
     * @return a new classlaoder
     */
    ClassLoader buildFrom(ClassLoader cl, Function<Function<Logger, Void>, Void> addEndOfLife, Logger logger);


}
