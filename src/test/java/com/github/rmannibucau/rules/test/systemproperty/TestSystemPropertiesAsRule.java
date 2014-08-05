package com.github.rmannibucau.rules.test.systemproperty;

import com.github.rmannibucau.rules.api.systemproperty.PlaceHoldableSystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperties;
import com.github.rmannibucau.rules.api.systemproperty.SystemProperty;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestSystemPropertiesAsRule {
	@Rule
	public final PlaceHoldableSystemProperties unit = new PlaceHoldableSystemProperties(this);

    private final String value = "V";
    private final int value2 = 4;

	@Test
	@SystemProperties(@SystemProperty(key = "foo.txt", value = "bar.txt"))
	public void checkInjections() throws IOException {
		assertEquals("bar.txt", System.getProperty("foo.txt"));
	}

	@Test
	@SystemProperties({
        @SystemProperty(key = "f", value = "#{value}"),
        @SystemProperty(key = "f2", value = "#{value2}")
    })
	public void checkField() throws IOException {
		assertEquals("V", System.getProperty("f"));
		assertEquals("4", System.getProperty("f2"));
	}

	@AfterClass
	public static void checkItWasCleanedUp() {
		assertNull(System.getProperty("foo.txt"));
	}
}
