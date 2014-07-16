package com.github.rmannibucau.rules.test.hazelcast;

import org.junit.Rule;
import org.junit.Test;

import com.github.rmannibucau.rules.api.hazelcast.HazelcastRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HazelcastRuleTest {
	@Rule
	public final HazelcastRule rule = new HazelcastRule();

	@Test
	public void run() {
		assertNotNull(rule.getInstance());
		assertTrue(rule.getInstance().getLifecycleService().isRunning());
	}
}
