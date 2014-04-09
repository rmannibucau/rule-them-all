package com.github.rmannibucau.rules.api.spring;

import java.lang.reflect.Method;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

public class SpringRule implements TestRule {
	private final Object testInstance;

	public SpringRule(final Object testInstance) {
		this.testInstance = testInstance;
	}

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				ContextConfiguration cc = description.getAnnotation(ContextConfiguration.class);
				if (cc == null) {
					cc = description.getTestClass().getAnnotation(ContextConfiguration.class);
				}

				if (cc == null) {
					base.evaluate();
					return;
				}

				final TestContextManager tcm = new TestContextManager(description.getTestClass());
				tcm.prepareTestInstance(testInstance);
				final Method mtd = description.getTestClass().getMethod(description.getMethodName());
				tcm.beforeTestMethod(testInstance, mtd);
				try {
					base.evaluate();
					tcm.afterTestMethod(testInstance, mtd, null);
				} catch (final Throwable t ) {
					tcm.afterTestMethod(testInstance, mtd, t);
					throw t;
				}
			}
		};
	}
}
