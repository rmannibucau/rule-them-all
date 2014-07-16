package com.github.rmannibucau.rules.test.project;

import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.junit.Test;

import com.github.rmannibucau.rules.api.project.BaseDirRule;

public class BaseDirRuleTest {
	@Rule
	public final BaseDirRule rule = new BaseDirRule();

	@Test
	public void ensureSystemPropIsAvailable() {
		assertNotNull(System.getProperty(rule.getPropName()));
		assertNotNull(System.getProperty("project.basedir"));
	}
}
