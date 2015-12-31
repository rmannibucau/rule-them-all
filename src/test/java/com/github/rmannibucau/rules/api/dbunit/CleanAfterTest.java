package com.github.rmannibucau.rules.api.dbunit;

import org.h2.jdbcx.JdbcDataSource;
import org.jboss.arquillian.persistence.ApplyScriptBefore;
import org.jboss.arquillian.persistence.Cleanup;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.jboss.arquillian.persistence.TestExecutionPhase.AFTER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Cleanup(phase = AFTER)
@ApplyScriptBefore("datasets/user_table.sql")
public class CleanAfterTest {
    @Rule
    public final TestRule dbunit = RuleChain
        .outerRule(new TestRule() {
            @Override
            public Statement apply(final Statement base, final Description description) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        base.evaluate();

                        // check after the dbunit rule is done and has cleaned up the data
                        try (final Connection c = ds.getConnection()) {
                            try (final PreparedStatement s = c.prepareStatement("select * from useraccount order by id")) {
                                try (final ResultSet r = s.executeQuery()) {
                                    assertFalse(r.next());
                                }
                            }
                        }
                    }
                };
            }
        }).around(new ArquillianPersistenceDbUnitRule().resourcesHolder(this));

    // in practise spring or app composer can inject it
    @DbUnitInstance
    private static DataSource ds;

    @BeforeClass
    public static void createDs() {
        final JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setUser("SA");
        jdbcDataSource.setPassword("");
        jdbcDataSource.setUrl("jdbc:h2:mem:" + CleanAfterTest.class.getSimpleName() + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1");
        ds = jdbcDataSource;
    }

    @Test
    @UsingDataSet("datasets/users.yml")
    public void run() throws SQLException {
        try (final Connection c = ds.getConnection()) {
            try (final PreparedStatement s = c.prepareStatement("select * from useraccount order by id")) {
                try (final ResultSet resultSet = s.executeQuery()) {
                    {
                        assertTrue(resultSet.next());
                        assertEquals(1, resultSet.getLong("id"));
                        assertEquals("John", resultSet.getString("firstname"));
                        assertEquals("Smith", resultSet.getString("lastname"));
                        assertEquals("doovde", resultSet.getString("username"));
                        assertEquals("password", resultSet.getString("password"));
                    }
                    {
                        assertTrue(resultSet.next());
                        assertEquals(2, resultSet.getLong("id"));
                        assertEquals("Clark", resultSet.getString("firstname"));
                        assertEquals("Kent", resultSet.getString("lastname"));
                        assertEquals("superman", resultSet.getString("username"));
                        assertEquals("kryptonite", resultSet.getString("password"));
                    }
                    assertFalse(resultSet.next());
                }
            }
        }
    }
}
