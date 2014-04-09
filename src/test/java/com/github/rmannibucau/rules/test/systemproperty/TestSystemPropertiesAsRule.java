package com.github.rmannibucau.rules.test.systemproperty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import com.github.rmannibucau.rules.api.systemproperty.PlaceHoldableSystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperty;

public class TestSystemPropertiesAsRule {
	@Rule
	public final PlaceHoldableSystemProperties unit = new PlaceHoldableSystemProperties();

	@Test
	@SystemProperties(@SystemProperty(key = "foo.txt", value = "bar.txt"))
	public void checkInjections() throws IOException {
		assertEquals("bar.txt", System.getProperty("foo.txt"));
	}

	@AfterClass
	public static void checkItWasCleanedUp() {
		assertNull(System.getProperty("foo.txt"));
	}
}
