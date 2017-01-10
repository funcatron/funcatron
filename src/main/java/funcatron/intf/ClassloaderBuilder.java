package funcatron.intf;

/**
 * A service that will take a classloader and layer another
 * classloader on top of it. This is useful for stuff like Spring
 * where they have magic classloaders.
 */
public interface ClassloaderBuilder {
    /**
     * Given a base classloader (or one vended from another ClassloaderBuilder), return one
     * @param cl the current classlaoder
     * @return a new classlaoder
     */
    ClassLoader buildFrom(ClassLoader cl);
}
