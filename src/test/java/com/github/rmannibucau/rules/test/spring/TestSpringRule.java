package com.github.rmannibucau.rules.test.spring;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;

import com.github.rmannibucau.rules.api.spring.SpringRule;

@ContextConfiguration("classpath:appCtx.xml")
public class TestSpringRule {
	@Rule
	public final SpringRule rule = new SpringRule(this);

	@Autowired
	@Qualifier("foo")
	private String fooSpringBean;

	@Test
	public void run() {
		assertEquals("test", fooSpringBean);
	}
}
