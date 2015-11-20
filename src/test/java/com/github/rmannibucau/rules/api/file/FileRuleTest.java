package com.github.rmannibucau.rules.api.file;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class FileRuleTest {
    @Rule
    public final FileRule rule = new FileRule();

    @Test
    @CreateFile(path = "target/in/file.txt", content = "content")
    @ExpectedOutput(path = "target/in/file.txt", content = "content")
    public void run() {
        // no-op
    }

    @Test
    public void noProblemIfNoAnnotation() {
        // no-op
    }

    @Test
    public void ensureCodeIsCalled() throws Throwable {
        new FileRule().apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assertEquals("content 2", FileUtils.readFileToString(new File("target/in2/file.txt")));
            }
        }, Description.createSuiteDescription("create", White.class.getMethod("create").getAnnotations())).evaluate();
        assertFalse(new File("target/in2/file.txt").isFile());

        try {
            new FileRule().apply(new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    // no-op
                }
            }, Description.createSuiteDescription("expected", White.class.getMethod("asserts").getAnnotations())).evaluate();
            fail();
        } catch (final AssertionError ae) {
            // ok
        }
    }

    public static class White {
        @CreateFile(path = "target/in2/file.txt", content = "content 2")
        public void create() {
            // no-op
        }

        @ExpectedOutput(path = "target/in-missing/file.txt", content = "anything")
        public void asserts() {
            // no-op
        }
    }
}
