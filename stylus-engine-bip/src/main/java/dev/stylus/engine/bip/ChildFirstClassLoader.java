package dev.stylus.engine.bip;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * Child-first classloader over the user's BIP jars (docs/03): Oracle classes resolve from the
 * installation, everything else from the JDK platform loader. The parent is the *platform*
 * classloader, not the application one, so the Oracle runtime can never see or clash with our
 * Saxon/FOP/JavaFX classpath — and we never leak Oracle types beyond the reflection facade.
 */
final class ChildFirstClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    ChildFirstClassLoader(List<Path> jars) {
        super("stylus-bip",
                jars.stream().map(ChildFirstClassLoader::toUrl).toArray(URL[]::new),
                ClassLoader.getPlatformClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name); // child first
                } catch (ClassNotFoundException e) {
                    loaded = super.loadClass(name, false); // then platform/JDK
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad jar path: " + path, e);
        }
    }
}
