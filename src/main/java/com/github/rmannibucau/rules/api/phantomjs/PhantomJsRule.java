package com.github.rmannibucau.rules.api.phantomjs;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import static com.github.rmannibucau.rules.internal.JarLocation.jarFromPrefix;
import static com.github.rmannibucau.rules.internal.Zips.unzip;

public class PhantomJsRule implements TestRule {
    private final DesiredCapabilities capabilities;

    private PhantomJSDriver driver;

    public PhantomJsRule() {
        this(null);
    }

    public PhantomJsRule(final DesiredCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    public String get(final String url) {
        driver.get(url);
        return driver.getPageSource();
    }

    @Override
    public Statement apply(final Statement statement, final Description description) {
        final File phantomJs = new File("target/phantomjs");
        if (!phantomJs.exists() && !phantomJs.mkdirs()) {
            throw new IllegalStateException("Can't create " + phantomJs.getAbsolutePath());
        }
        try {
            unzip(jarFromPrefix("arquillian-phantom-binary"), phantomJs);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }

        final File exec = new File(phantomJs, "bin/phantomjs" + (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win") ? ".exe" : ""));
        exec.setExecutable(true);

        final PhantomJSDriverService service;
        if (capabilities != null) {
            capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, exec.getAbsolutePath());
            service = PhantomJSDriverService.createDefaultService(capabilities);
        } else {
            service = new PhantomJSDriverService.Builder().usingPhantomJSExecutable(exec).usingAnyFreePort().build();
        }

        driver = new PhantomJSDriver(service, capabilities == null ? DesiredCapabilities.chrome() : capabilities);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                service.start();
                try {
                    statement.evaluate();
                } finally {
                    service.stop();
                }
            }
        };
    }

    public PhantomJSDriver getDriver() {
        return driver;
    }
}
