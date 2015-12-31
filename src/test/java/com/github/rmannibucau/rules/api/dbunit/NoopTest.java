package com.github.rmannibucau.rules.api.dbunit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertTrue;

// when not used it doesn't fail
public class NoopTest {
    @Rule
    public final TestRule ignored = new ArquillianPersistenceDbUnitRule();

    @Test
    public void run()  {
        assertTrue(true);
    }
}
