package com.github.rmannibucau.rules.api.systemproperty;

import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.apache.commons.lang3.text.StrLookup.mapLookup;

public class PlaceHoldableSystemProperties implements TestRule {
    private final Object instance;

    public PlaceHoldableSystemProperties() {
        this(null);
    }

    public PlaceHoldableSystemProperties(final Object instance) {
        this.instance = instance;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (description.getAnnotation(SystemProperties.class) == null
                        && description.getTestClass().getAnnotation(SystemProperties.class) == null) {
                    base.evaluate();
                    return;
                }

                final Properties original = new Properties();
                final Properties systProps = System.getProperties();
                original.clear();
                original.putAll(systProps);

                final StrSubstitutor replacer1 = new StrSubstitutor(new DynamicStrLookup());
                final StrSubstitutor replacer2 = new StrSubstitutor(mapLookup(buildFromFields(instance)), "#{", "}", '\\');

                populateProperties(replacer1, replacer2, description.getAnnotation(SystemProperties.class));
                populateProperties(replacer1, replacer2, description.getTestClass().getAnnotation(SystemProperties.class));

                try {
                    base.evaluate();
                } finally {
                    final Properties sp = System.getProperties();
                    sp.clear();
                    sp.putAll(original);
                }
            }
        };
    }

    private void populateProperties(final StrSubstitutor propReplacer, final StrSubstitutor fieldReplacer,
                                    final SystemProperties annotation) {
        if (annotation == null) {
            return;
        }

        final Properties systProps = System.getProperties();
        for (final SystemProperty sp : annotation.value()) {
            systProps.put(sp.key(), fieldReplacer.replace(propReplacer.replace(sp.value())));
        }
        for (final String file : annotation.files()) {
            final Properties p = new Properties();

            final File f = new File(file);
            if (!f.isFile()) {
                final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
                if (is != null) {
                    try {
                        systProps.load(is);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        try {
                            is.close();
                        } catch (final IOException e) {
                            // no-op
                        }
                    }
                }
                throw new IllegalArgumentException(file + " doesn't exist");
            } else {
                FileInputStream stream = null;
                try {
                    stream = new FileInputStream(file);
                    p.load(stream);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (final IOException e) {
                            // no-op
                        }
                    }
                }
            }

            // filter them
            for (final String key : p.stringPropertyNames()) {
                p.put(key, fieldReplacer.replace(propReplacer.replace(p.getProperty(key))));
            }

            systProps.putAll(p);
        }
    }

    private static Map<String, Object> buildFromFields(final Object instance) {
        if (instance == null) {
            return Collections.emptyMap();
        }
        Class<?> current = instance.getClass();
        final Map<String, Object> values = new HashMap<String, Object>();
        while (current != Object.class && !current.isInterface()) {
            for (final Field f : current.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    values.put(f.getName(), f.get(instance));
                } catch (final Throwable e) {
                    // no-op
                }
            }
            current = current.getSuperclass();
        }
        return values;
    }

    // by default system properties are cached so if we add one it is ignored
    private static class DynamicStrLookup extends StrLookup<String> {
        @Override
        public String lookup(final String key) {
            return System.getProperty(key);
        }
    }
}

