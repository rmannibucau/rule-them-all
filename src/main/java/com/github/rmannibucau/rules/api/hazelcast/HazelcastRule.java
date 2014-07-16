package com.github.rmannibucau.rules.api.hazelcast;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public class HazelcastRule implements TestRule {
	private final Config config;
	private HazelcastInstance instance = null;

	public HazelcastRule() {
		this(null);
	}

	public HazelcastRule(final Config config) {
		this.config = config;
	}

	public HazelcastInstance getInstance() {
		return instance;
	}

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				instance = Hazelcast.newHazelcastInstance(config != null ? config : new XmlConfigBuilder().build());
				try {
					base.evaluate();
				} finally {
					instance.getLifecycleService().shutdown();
				}
			}
		};
	}
}
