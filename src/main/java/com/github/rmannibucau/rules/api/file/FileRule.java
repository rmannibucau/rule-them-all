package com.github.rmannibucau.rules.api.file;

import org.apache.commons.io.FileUtils;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileRule implements TestRule {
    private final ThreadLocal<Description> current = new ThreadLocal<Description>();

    @Override
    public Statement apply(final Statement base, final Description description) {
        final CreateFile input = description.getAnnotation(CreateFile.class);
        final CreateFiles inputs = description.getAnnotation(CreateFiles.class);
        final ExpectedOutput output = description.getAnnotation(ExpectedOutput.class);
        final ExpectedOutputs outputs = description.getAnnotation(ExpectedOutputs.class);
        return input == null && output == null && inputs == null && outputs == null ? base : new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Collection<File> inputFiles = new ArrayList<File>();
                if (input != null) {
                    createFile(inputFiles, input);
                }
                if (inputs != null) {
                    for (final CreateFile file : inputs.value()) {
                        createFile(inputFiles, file);
                    }
                }

                current.set(description);
                try {
                    base.evaluate();
                    if (output != null) {
                        assertOutput(output);
                    }
                    if (outputs != null) {
                        for (final ExpectedOutput o : outputs.value()) {
                            assertOutput(o);
                        }
                    }
                } finally {
                    current.remove();
                    for (final File file : inputFiles) {
                        FileUtils.deleteQuietly(file);
                    }
                }
            }

            private void assertOutput(final ExpectedOutput o) throws IOException {
                final File file = new File(o.path());
                assertTrue(file.isFile());
                assertEquals(o.content(), FileUtils.readFileToString(file));
            }

            private void createFile(final Collection<File> inputFiles, final CreateFile input) throws IOException {
                final File inputFile = new File(input.path());
                FileUtils.forceMkdir(inputFile.getParentFile());
                FileUtils.write(inputFile, input.content());
                inputFiles.add(inputFile);
            }
        };
    }

    public String input() {
        final Description value = current.get();
        if (value == null) {
            return null;
        }
        return value.getAnnotation(CreateFile.class).path();
    }

    public String[] inputs() {
        final Description value = current.get();
        if (value == null) {
            return null;
        }
        final CreateFile[] createFiles = value.getAnnotation(CreateFiles.class).value();
        final String[] paths = new String[createFiles.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = createFiles[i].path();
        }
        return paths;
    }

    public String output() {
        final Description value = current.get();
        if (value == null) {
            return null;
        }
        return value.getAnnotation(ExpectedOutput.class).path();
    }

    public String[] outputs() {
        final Description value = current.get();
        if (value == null) {
            return null;
        }
        final ExpectedOutput[] expectedOutputs = value.getAnnotation(ExpectedOutputs.class).value();
        final String[] paths = new String[expectedOutputs.length];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = expectedOutputs[i].path();
        }
        return paths;
    }
}
