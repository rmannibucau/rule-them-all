package com.github.rmannibucau.rules.internal;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.regex.Pattern;

public final class JarLocation {
    private JarLocation() {
        // no-op
    }

    public static File jarFromPrefix(final String prefix) {
        return jarFromRegex(prefix + ".*\\.jar");
    }

    public static File jarFromRegex(final String regex) {
        final Pattern pattern = Pattern.compile(regex);
        try {
            final Set<URL> urls = ClassLoaders.findUrls(Thread.currentThread().getContextClassLoader());
            for (final URL url : urls) {
                final File f = new File(ClassLoaders.decode(url.getFile()));
                if (f.exists() && pattern.matcher(f.getName()).matches()) {
                    return f;
                }
            }
            throw new IllegalArgumentException(regex + " not found in " + urls);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static File jarFromResource(final String resourceName) {
        return jarFromResource(Thread.currentThread().getContextClassLoader(), resourceName);
    }

    public static File jarFromResource(final ClassLoader loader, final String resourceName) {
        try {
            URL url = loader.getResource(resourceName);
            if (url == null) {
                throw new IllegalStateException("classloader.getResource(classFileName) returned a null URL");
            }

            if ("jar".equals(url.getProtocol())) {
                final String spec = url.getFile();

                int separator = spec.indexOf('!');
                if (separator == -1) {
                    throw new MalformedURLException("no ! found in jar url spec:" + spec);
                }

                url = new URL(spec.substring(0, separator++));

                return new File(ClassLoaders.decode(url.getFile()));

            } else if ("file".equals(url.getProtocol())) {
                return toFile(resourceName, url);
            } else {
                throw new IllegalArgumentException("Unsupported URL scheme: " + url.toExternalForm());
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static File jarLocation(final Class clazz) {
        try {
            final String classFileName = clazz.getName().replace(".", "/") + ".class";
            final ClassLoader loader = clazz.getClassLoader();
            return jarFromResource(loader, classFileName);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static File toFile(final String classFileName, final URL url) {
        String path = url.getFile();
        path = path.substring(0, path.length() - classFileName.length());
        return new File(ClassLoaders.decode(path));
    }
}
