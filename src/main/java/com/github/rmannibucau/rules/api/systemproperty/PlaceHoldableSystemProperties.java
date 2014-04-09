package com.github.rmannibucau.rules.api.systemproperty;

import java.util.Properties;

import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class PlaceHoldableSystemProperties implements TestRule {
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
				populateProperties(description.getAnnotation(SystemProperties.class));
				populateProperties(description.getTestClass().getAnnotation(SystemProperties.class));

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

	private static void populateProperties(final SystemProperties annotation) {
		if (annotation == null) {
			return;
		}

		final StrSubstitutor replacer = new StrSubstitutor(new DynamicStrLookup());

		final Properties systProps = System.getProperties();
		for (final SystemProperty sp : annotation.value()) {
			systProps.put(sp.key(), replacer.replace(sp.value()));
		}
	}

	// by default system properties are cached so if we add one it is ignored
	private static class DynamicStrLookup extends StrLookup<String> {
		@Override
		public String lookup(final String key) {
			return System.getProperty(key);
		}
	}
}
