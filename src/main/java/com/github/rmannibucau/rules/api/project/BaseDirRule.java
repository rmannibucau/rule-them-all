package com.github.rmannibucau.rules.api.project;

import java.io.File;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class BaseDirRule implements TestRule {
	private final String propName;
	private final File baseDir;

	public BaseDirRule() {
		this("project.basedir", new File("."));
	}

	public BaseDirRule(final String propName, final File baseDir) {
		this.propName = propName;
		this.baseDir = baseDir;
	}

	public String getPropName() {
		return propName;
	}

	public File getBaseDir() {
		return baseDir;
	}

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				final String old = System.getProperty(propName);
				System.setProperty(propName, baseDir.getAbsolutePath());
				try {
					base.evaluate();
				}
				finally {
					if (old == null) {
						System.clearProperty(propName);
					} else {
						System.setProperty(propName, old);
					}
				}
			}
		};
	}
}
