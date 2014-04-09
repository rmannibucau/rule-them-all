package com.github.rmannibucau.rules.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedList;

import com.github.rmannibucau.rules.api.LifecycleUnitException;

public class Reflections {
	public static void set(final Field f, final Object instance, final Object value) {
		setAccessible(f);
		try {
			f.set(instance, value);
		} catch (final IllegalAccessException e) {
			throw new LifecycleUnitException(e);
		}
	}

	public static Collection<Field> findFields(final Class<?> clazz, final Class<? extends Annotation> annotation) {
		final Collection<Field> fields = new LinkedList<Field>();
		Class<?> current = clazz;
		while (current != null && current != Object.class && !current.isInterface()) {
			for (final Field f : current.getDeclaredFields()) {
				if (annotation != null && f.getAnnotation(annotation) == null) {
					continue;
				}

				fields.add(f);
			}
			current = current.getSuperclass();
		}
		return fields;
	}

	private Reflections() {
		// no-op
	}

	public static <T> T get(final Field f, final Object instance, final Class<T> type) {
		setAccessible(f);
		try {
			final Object value = f.get(instance);
			if (value == null || type == null) {
				return (T) value;
			}
			return type.cast(value);
		} catch (final Exception e) {
			throw new LifecycleUnitException(e);
		}
	}

	private static void setAccessible(final Field f) {
		if (!f.isAccessible()) {
			f.setAccessible(true);
		}
	}
}
