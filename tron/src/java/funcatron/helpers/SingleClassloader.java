package funcatron.helpers;

import java.net.URLClassLoader;

/**
 * A classloader that loads a single class
 */
public class SingleClassloader extends ClassLoader {
    private final String name;
    private final byte[] clz;

    public SingleClassloader(String name, byte[] clz, ClassLoader parent) {
        super(parent);
        this.name = name;
        this.clz = clz;
    }

    /**
     * Finds the class with the specified <a href="#name">binary name</a>.
     * This method should be overridden by class loader implementations that
     * follow the delegation model for loading classes, and will be invoked by
     * the {@link #loadClass <tt>loadClass</tt>} method after checking the
     * parent class loader for the requested class.  The default implementation
     * throws a <tt>ClassNotFoundException</tt>.
     *
     * @param  name
     *         The <a href="#name">binary name</a> of the class
     *
     * @return  The resulting <tt>Class</tt> object
     *
     * @throws  ClassNotFoundException
     *          If the class could not be found
     *
     * @since  1.2
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name == this.name) {
            this.defineClass(name, clz, 0, clz.length);
        }
        throw new ClassNotFoundException(name);
    }
}
