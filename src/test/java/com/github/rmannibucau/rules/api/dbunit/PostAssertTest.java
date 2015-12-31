package com.github.rmannibucau.rules.api.dbunit;

import org.h2.jdbcx.JdbcDataSource;
import org.jboss.arquillian.persistence.ApplyScriptBefore;
import org.jboss.arquillian.persistence.Cleanup;
import org.jboss.arquillian.persistence.CleanupStrategy;
import org.jboss.arquillian.persistence.ShouldMatchDataSet;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.jboss.arquillian.persistence.TestExecutionPhase.AFTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

@Cleanup(phase = AFTER, strategy = CleanupStrategy.USED_TABLES_ONLY)
@ApplyScriptBefore("datasets/user_table.sql")
public class PostAssertTest {
    @Rule
    public final TestRule dbunit = new TestRule() {
            @Override
            public Statement apply(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        error.set(null);
                        try {
                            new ArquillianPersistenceDbUnitRule().resourcesHolder(PostAssertTest.this)
                                .apply(base, description)
                                .evaluate();
                        } catch (final Throwable err) {
                            error.set(err);
                        } finally {
                            for (final Runnable t : validateTasks) {
                                t.run();
                            }
                        }
                    }
                };
            }
        };

    // in practise spring or app composer can inject it
    @DbUnitInstance
    private static DataSource ds;

    private static final AtomicReference<Throwable> error = new AtomicReference<>();
    private static final Collection<Runnable> validateTasks = new ArrayList<>();

    @BeforeClass
    public static void createDs() {
        final JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setUser("SA");
        jdbcDataSource.setPassword("");
        jdbcDataSource.setUrl("jdbc:h2:mem:" + PostAssertTest.class.getSimpleName() + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1");
        ds = jdbcDataSource;
    }

    @AfterClass
    public static void ensureAllWentGoodAfterValidations() {
        ensureNoError();
    }

    @Test
    @UsingDataSet("datasets/users.yml")
    @ShouldMatchDataSet("datasets/users2.yml")
    public void success() throws SQLException { // check it works in passing case
        try (final Connection c = ds.getConnection()) {
            try (final java.sql.Statement s = c.createStatement()) {
                assertEquals(1, s.executeUpdate("update useraccount set password='nothing' where id=2"));
            }
        }
        validateTasks.clear();
        validateTasks.add(new Runnable() {
            @Override
            public void run() {
                ensureNoError();
            }
        });
    }

    @Test
    @UsingDataSet("datasets/users.yml")
    @ShouldMatchDataSet("datasets/users2.yml")
    public void failure() throws SQLException { // check it fails in not passing case
        // not the expected password
        validateTasks.clear();
        validateTasks.add(new Runnable() {
            @Override
            public void run() {
                final Throwable exception = error.get();
                assertNotNull(exception);
                assertThat(exception, instanceOf(AssertionError.class));
                error.set(null);
            }
        });
    }

    private static void ensureNoError() {
        final Throwable error = PostAssertTest.error.get();
        if (error != null) {
            error.printStackTrace();
        }
        assertNull(error);
    }
}
