package eu.ecmx.doxis.agent;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * URL class loader that exposes the `addURL` method in URLClassLoader.
 */
public class MutableUrlClassLoader extends URLClassLoader {
    
    static {
        ClassLoader.registerAsParallelCapable();
    }
    
    public MutableUrlClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }
    
    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }
}