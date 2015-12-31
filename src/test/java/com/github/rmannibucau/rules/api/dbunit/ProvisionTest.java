package com.github.rmannibucau.rules.api.dbunit;

import org.h2.jdbcx.JdbcDataSource;
import org.jboss.arquillian.persistence.ApplyScriptBefore;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@ApplyScriptBefore("datasets/user_table.sql")
public class ProvisionTest {
    @Rule
    public final TestRule dbunit = new ArquillianPersistenceDbUnitRule().resourcesHolder(this);

    // in practise spring or app composer can inject it
    @DbUnitInstance
    private static DataSource ds;

    @BeforeClass
    public static void createDs() {
        final JdbcDataSource jdbcDataSource = new JdbcDataSource();
        jdbcDataSource.setUser("SA");
        jdbcDataSource.setPassword("");
        jdbcDataSource.setUrl("jdbc:h2:mem:" + ProvisionTest.class.getSimpleName() + System.currentTimeMillis());
        ds = jdbcDataSource;
    }

    @Test
    @UsingDataSet("datasets/users.yml")
    public void run() throws SQLException {
        try (final Connection c = ds.getConnection()) {
            try (final PreparedStatement s = c.prepareStatement("select * from useraccount order by id")) {
                final ResultSet resultSet = s.executeQuery();
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

    @Test
    public void noop() {
        assertTrue(true);
    }
}
