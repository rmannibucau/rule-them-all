package com.github.rmannibucau.rules.api.dbunit;

import org.dbunit.DatabaseUnitException;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.datatype.DefaultDataTypeFactory;
import org.dbunit.dataset.filter.ExcludeTableFilter;
import org.dbunit.dataset.filter.IColumnFilter;
import org.dbunit.dataset.filter.ITableFilter;
import org.dbunit.ext.db2.Db2DataTypeFactory;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.dbunit.ext.hsqldb.HsqldbDataTypeFactory;
import org.dbunit.ext.mysql.MySqlDataTypeFactory;
import org.dbunit.ext.oracle.OracleDataTypeFactory;
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.jboss.arquillian.persistence.CleanupUsingScript;
import org.jboss.arquillian.persistence.DataSeedStrategy;
import org.jboss.arquillian.persistence.ShouldMatchDataSet;
import org.jboss.arquillian.persistence.core.configuration.PersistenceConfiguration;
import org.jboss.arquillian.persistence.core.event.AfterPersistenceTest;
import org.jboss.arquillian.persistence.core.event.BeforePersistenceTest;
import org.jboss.arquillian.persistence.core.metadata.MetadataExtractor;
import org.jboss.arquillian.persistence.core.metadata.PersistenceExtensionEnabler;
import org.jboss.arquillian.persistence.core.metadata.PersistenceExtensionFeatureResolver;
import org.jboss.arquillian.persistence.core.metadata.PersistenceExtensionScriptingFeatureResolver;
import org.jboss.arquillian.persistence.core.test.AssertionErrorCollector;
import org.jboss.arquillian.persistence.dbunit.DataSetComparator;
import org.jboss.arquillian.persistence.dbunit.api.CustomColumnFilter;
import org.jboss.arquillian.persistence.dbunit.cleanup.CleanupStrategyExecutor;
import org.jboss.arquillian.persistence.dbunit.cleanup.CleanupStrategyProvider;
import org.jboss.arquillian.persistence.dbunit.configuration.DBUnitConfiguration;
import org.jboss.arquillian.persistence.dbunit.configuration.DBUnitConfigurationPropertyMapper;
import org.jboss.arquillian.persistence.dbunit.configuration.DBUnitDataSeedStrategyProvider;
import org.jboss.arquillian.persistence.dbunit.data.descriptor.DataSetResourceDescriptor;
import org.jboss.arquillian.persistence.dbunit.data.provider.DataSetProvider;
import org.jboss.arquillian.persistence.dbunit.data.provider.ExpectedDataSetProvider;
import org.jboss.arquillian.persistence.dbunit.dataset.DataSetRegister;
import org.jboss.arquillian.persistence.dbunit.filter.TableFilterResolver;
import org.jboss.arquillian.persistence.jpa.cache.JpaCacheEvictionConfiguration;
import org.jboss.arquillian.persistence.jpa.cache.JpaCacheEvictionHandler;
import org.jboss.arquillian.persistence.script.ScriptExecutor;
import org.jboss.arquillian.persistence.script.configuration.ScriptingConfiguration;
import org.jboss.arquillian.persistence.script.data.descriptor.SqlScriptResourceDescriptor;
import org.jboss.arquillian.persistence.script.data.provider.SqlScriptProvider;
import org.jboss.arquillian.persistence.script.splitter.DefaultStatementSplitter;
import org.jboss.arquillian.persistence.script.splitter.StatementSplitterResolver;
import org.jboss.arquillian.persistence.spi.dbunit.filter.TableFilterProvider;
import org.jboss.arquillian.persistence.spi.script.StatementSplitter;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.TestEvent;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.jboss.arquillian.persistence.dbunit.DataSetUtils.mergeDataSets;

// a rule reusing arquillian persistence api
public class ArquillianPersistenceDbUnitRule implements TestRule {
    private final ThreadLocal<Configuration> configurationThreadLocal = new ThreadLocal<Configuration>();

    public ArquillianPersistenceDbUnitRule() {
        configurationThreadLocal.set(new Configuration());
    }

    public ArquillianPersistenceDbUnitRule resourcesHolder(final Object test) {
        configurationThreadLocal.get().providerInstance(test);
        return this;
    }

    public ArquillianPersistenceDbUnitRule persistenceConfiguration(final PersistenceConfiguration configuration) {
        configurationThreadLocal.get().persistenceConfiguration(configuration);
        return this;
    }

    public ArquillianPersistenceDbUnitRule jpaCacheEvictionConfiguration(final JpaCacheEvictionConfiguration configuration) {
        configurationThreadLocal.get().jpaCacheEvictionConfiguration(configuration);
        return this;
    }

    public ArquillianPersistenceDbUnitRule scriptingConfiguration(final ScriptingConfiguration configuration) {
        configurationThreadLocal.get().scriptingConfiguration(configuration);
        return this;
    }

    public ArquillianPersistenceDbUnitRule dbUnitConfiguration(final DBUnitConfiguration configuration) {
        configurationThreadLocal.get().dbUnitConfiguration(configuration);
        return this;
    }

    public ArquillianPersistenceDbUnitRule entityManager(final String name, EntityManager entityManager) {
        configurationThreadLocal.get().entityManager(name, entityManager);
        return this;
    }

    public ArquillianPersistenceDbUnitRule dataSource(final DataSource ds) {
        configurationThreadLocal.get().dataSource = ds;
        return this;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final State state = before(description);
                try {
                    base.evaluate();
                } finally {
                    after(state);
                }
            }
        };
    }

    private State before(final Description description) {
        final Method testMethod;
        try {
            testMethod = description.getTestClass().getMethod(description.getMethodName());
        } catch (final NoSuchMethodException e) {
            throw new IllegalArgumentException(e);
        }

        final MetadataExtractor metadataExtractor = new MetadataExtractor(new TestClass(description.getTestClass()));
        if (!new PersistenceExtensionEnabler(metadataExtractor).shouldPersistenceExtensionBeActivated()) {
            return null;
        }

        final State state = new State();
        state.method(testMethod);
        state.metaExtractor(metadataExtractor);

        final DataSource dataSource = findDataSource();
        if (dataSource == null) {
            throw new IllegalStateException("DataSource is not yet initialized");
        }

        final Configuration config = configurationThreadLocal.get();
        final PersistenceConfiguration configuration = config.persistenceConfiguration != null ? config.persistenceConfiguration : new PersistenceConfiguration();
        config.persistenceConfiguration(configuration);

        final JpaCacheEvictionConfiguration jpaCacheEvictionConfiguration = config.jpaCacheEvictionConfiguration != null ? config.jpaCacheEvictionConfiguration : new JpaCacheEvictionConfiguration();
        config.jpaCacheEvictionConfiguration(jpaCacheEvictionConfiguration);

        final ScriptingConfiguration scriptingConfiguration = config.scriptingConfiguration != null ? config.scriptingConfiguration : new ScriptingConfiguration();
        config.scriptingConfiguration(scriptingConfiguration);

        final DBUnitConfiguration dbUnitConfiguration = config.dbUnitConfiguration != null ? config.dbUnitConfiguration : new DBUnitConfiguration();
        config.dbUnitConfiguration(dbUnitConfiguration);

        final PersistenceExtensionFeatureResolver persistenceExtensionFeatureResolver = new PersistenceExtensionFeatureResolver(testMethod, metadataExtractor, configuration);
        state.persistenceExtensionFeatureResolver(persistenceExtensionFeatureResolver);
        final PersistenceExtensionScriptingFeatureResolver persistenceExtensionScriptingFeatureResolver = new PersistenceExtensionScriptingFeatureResolver(testMethod, metadataExtractor, scriptingConfiguration);
        state.persistenceExtensionScriptingFeatureResolver(persistenceExtensionScriptingFeatureResolver);

        DataSetRegister dataSetRegister = null;
        if (persistenceExtensionFeatureResolver.shouldVerifyDataAfterTest()) {
            final Collection<DataSetResourceDescriptor> dataSetResourceDescriptors = new ExpectedDataSetProvider(metadataExtractor, dbUnitConfiguration).getDescriptorsDefinedFor(testMethod);
            for (final DataSetResourceDescriptor dataSetDescriptor : dataSetResourceDescriptors) {
                if (dataSetRegister == null) {
                    dataSetRegister = new DataSetRegister();
                    state.dataSetRegister(dataSetRegister);
                }
                dataSetRegister.addExpected(dataSetDescriptor.getContent());
            }
        }

        final String schema = dbUnitConfiguration.getSchema();
        final DatabaseConnection databaseConnection;
        try {
            databaseConnection = new DatabaseConnection(dataSource.getConnection(), schema == null || schema.isEmpty() ? null : schema);
            initConnection(databaseConnection);
            state.databaseConnection(databaseConnection);
        } catch (final DatabaseUnitException | SQLException e) {
            throw new IllegalStateException(e);
        }

        if (state.persistenceExtensionScriptingFeatureResolver.shouldCleanupUsingScriptBefore()) {
            executeScripts(
                state, config,
                SqlScriptProvider.createProviderForCleanupScripts(new TestClass(state.method.getDeclaringClass()), config.scriptingConfiguration).getDescriptorsDefinedFor(state.method));
        }
        if (state.persistenceExtensionFeatureResolver.shouldCustomScriptBeAppliedBeforeTestRequested()) {
            executeScripts(
                state, config,
                SqlScriptProvider.createProviderForScriptsToBeAppliedBeforeTest(new TestClass(state.method.getDeclaringClass()), config.scriptingConfiguration).getDescriptorsDefinedFor(state.method));
        }

        if (persistenceExtensionFeatureResolver.shouldSeedData()) {
            final Collection<DataSetResourceDescriptor> dataSetResourceDescriptors = new DataSetProvider(metadataExtractor, dbUnitConfiguration).getDescriptorsDefinedFor(testMethod);
            for (final DataSetResourceDescriptor dataSetDescriptor : dataSetResourceDescriptors) {
                if (dataSetRegister == null) {
                    dataSetRegister = new DataSetRegister();
                    state.dataSetRegister(dataSetRegister);
                }
                dataSetRegister.addInitial(dataSetDescriptor.getContent());
            }

            final DatabaseConfig dbUnitConfig = databaseConnection.getConfig();
            final Map<String, Object> properties = new DBUnitConfigurationPropertyMapper().map(dbUnitConfiguration);
            for (final Map.Entry<String, Object> property : properties.entrySet()) {
                if (DatabaseConfig.PROPERTY_DATATYPE_FACTORY.equals(property.getKey())) { // already set
                    continue;
                }
                dbUnitConfig.setProperty(property.getKey(), property.getValue());
            }

            if (dataSetRegister != null) {
                IDataSet initialDataSet;
                try {
                    initialDataSet = mergeDataSets(dataSetRegister.getInitial());
                } catch (final DataSetException e) {
                    throw new IllegalStateException(e);
                }

                if (dbUnitConfiguration.isFilterTables()) {
                    final TableFilterProvider sequenceFilterProvider = new TableFilterResolver(dbUnitConfiguration).resolve();
                    final ITableFilter databaseSequenceFilter;
                    try {
                        databaseSequenceFilter = sequenceFilterProvider.provide(databaseConnection, initialDataSet.getTableNames());
                    } catch (final SQLException | DataSetException e) {
                        throw new IllegalStateException(e);
                    }
                    initialDataSet = new FilteredDataSet(databaseSequenceFilter, initialDataSet);
                }

                final DataSeedStrategy dataSeedStrategy = persistenceExtensionFeatureResolver.getDataSeedStrategy();
                final boolean useIdentityInsert = dbUnitConfiguration.isUseIdentityInsert();
                try {
                    final DatabaseOperation databaseOperation = dataSeedStrategy.provide(new DBUnitDataSeedStrategyProvider(useIdentityInsert));
                    databaseOperation.execute(databaseConnection, initialDataSet);
                } catch (final DatabaseUnitException | SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        try {
            final JpaCacheEvictionHandler jpaHandler = new JpaCacheEvictionHandler(null, jpaCacheEvictionConfiguration) {
                @Override
                public EntityManager lookup(final String emJndiName) throws NamingException {
                    return findEntityManager(emJndiName);
                }
            };
            state.jpaEvictionHandler(jpaHandler);
            config.instance = config.instance == null ? description.getTestClass().newInstance() : config.instance;
            jpaHandler.onBeforeTestMethod(new BeforePersistenceTest(new TestEvent(config.instance, testMethod)));
        } catch (final InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        return state;
    }

    private void initConnection(final DatabaseConnection databaseConnection) {
        try {
            final DatabaseMetaData meta = databaseConnection.getConnection().getMetaData();
            final String product = meta.getDatabaseProductName().toLowerCase(Locale.ENGLISH);
            if (product.contains("h2")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new H2DataTypeFactory());
            } else if (product.contains("mysql")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new MySqlDataTypeFactory());
            } else if (product.contains("oracle")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new OracleDataTypeFactory());
            } else if (product.contains("hsql")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new HsqldbDataTypeFactory());
            } else if (product.contains("postgre")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new PostgresqlDataTypeFactory());
            } else if (product.contains("db2")) {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new Db2DataTypeFactory());
            } else {
                databaseConnection.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY, new DefaultDataTypeFactory());
            }
        } catch (final SQLException e) {
            // no-op
        }
    }

    private void after(final State state) {
        if (state == null) {
            return;
        }

        try {
            final Configuration configuration = configurationThreadLocal.get();

            state.jpaCacheEvictionHandler.onAfterTestMethod(new AfterPersistenceTest(new TestEvent(configuration.instance, state.method)));

            final AssertionErrorCollector errorCollector = new AssertionErrorCollector();
            if (state.persistenceExtensionFeatureResolver.shouldVerifyDataAfterTest()) {
                final ShouldMatchDataSet dataSetsToVerify = state.metadataExtractor.shouldMatchDataSet().fetchFrom(state.method);
                final CustomColumnFilter customColumnFilter = state.metadataExtractor.using(CustomColumnFilter.class).fetchFrom(state.method);
                try {
                    IDataSet currentDataSet = state.databaseConnection.createDataSet();
                    final String[] excludeTables = configuration.dbUnitConfiguration.getExcludeTablesFromComparisonWhenEmptyExpected();
                    if (excludeTables.length != 0) {
                        currentDataSet = new FilteredDataSet(new ExcludeTableFilter(excludeTables), currentDataSet);
                    }
                    final IDataSet expectedDataSet = mergeDataSets(state.dataSetRegister.getExpected());
                    final DataSetComparator dataSetComparator = new DataSetComparator(dataSetsToVerify.orderBy(),
                        dataSetsToVerify.excludeColumns(), customColumnFilter == null ? Collections.<Class<? extends IColumnFilter>>emptySet() : new HashSet<>(asList(customColumnFilter.value())));
                    dataSetComparator.compare(currentDataSet, expectedDataSet, errorCollector);
                } catch (final DatabaseUnitException | SQLException e) {
                    throw new IllegalStateException(e);
                }
            }

            if (state.persistenceExtensionFeatureResolver.shouldCleanupAfter()) {
                final CleanupStrategyExecutor cleanupStrategyExecutor = state.persistenceExtensionFeatureResolver.getCleanupStrategy()
                    .provide(new CleanupStrategyProvider(state.databaseConnection, state.dataSetRegister, configuration.dbUnitConfiguration));
                cleanupStrategyExecutor.cleanupDatabase(configuration.dbUnitConfiguration.getExcludeTablesFromCleanup());
            }

            if (state.persistenceExtensionScriptingFeatureResolver.shouldCleanupUsingScriptAfter()) {
                final SqlScriptProvider<CleanupUsingScript> scriptsProvider = SqlScriptProvider.createProviderForCleanupScripts(new TestClass(state.method.getDeclaringClass()), configuration.scriptingConfiguration);
                executeScripts(state, configuration, scriptsProvider.getDescriptorsDefinedFor(state.method));
            }

            errorCollector.report();
        } finally {
            try {
                state.databaseConnection.close();
            } catch (final SQLException e) {
                // no-op
            }
        }
    }

    private void executeScripts(final State state, final Configuration config, final Collection<SqlScriptResourceDescriptor> descriptors) {
        for (final SqlScriptResourceDescriptor scriptDescriptor : descriptors) {
            final String script = scriptDescriptor.getContent();
            // spi is done through SW so handle default manually
            final StatementSplitter statementSplitter = "default".equalsIgnoreCase(config.scriptingConfiguration.getSqlDialect()) ?
                new DefaultStatementSplitter() : new StatementSplitterResolver(config.scriptingConfiguration).resolve();
            try {
                new ScriptExecutor(state.databaseConnection.getConnection(), config.scriptingConfiguration, statementSplitter).execute(script);
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private DataSource findDataSource() {
        final Configuration configuration = configurationThreadLocal.get();
        if (configuration != null && configuration.dataSource != null) {
            return configuration.dataSource;
        }
        if (configuration != null && configuration.instance != null) { // TODO: support multiple datasources
            Class<?> c = configuration.instance.getClass();
            while (c != null && c != Object.class) {
                for (final Field f : c.getDeclaredFields()) {
                    if (f.getType() == DataSource.class && f.getAnnotation(DbUnitInstance.class) != null) {
                        if (!f.isAccessible()) {
                            f.setAccessible(true);
                        }
                        try {
                            return configuration.dataSource = DataSource.class.cast(f.get(Modifier.isStatic(f.getModifiers()) ? null : configuration.instance));
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                for (final Method m : c.getDeclaredMethods()) {
                    if (DataSource.class == m.getReturnType() && m.getAnnotation(DbUnitInstance.class) != null && m.getParameterTypes().length == 0) {
                        if (!m.isAccessible()) {
                            m.setAccessible(true);
                        }
                        try {
                            return configuration.dataSource = DataSource.class.cast(m.invoke(Modifier.isStatic(m.getModifiers()) ? null : configuration.instance));
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getCause());
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("No datasource available, provide either the tets instance with @DbUnitInstance on a datasource field or directly the datasource to the rule.");
    }

    private EntityManager findEntityManager(final String name) {
        final Configuration configuration = configurationThreadLocal.get();
        if (configuration != null && configuration.entityManagers.containsKey(name)) {
            return configuration.entityManagers.get(name);
        }
        if (configuration != null && configuration.instance != null) {
            Class<?> c = configuration.instance.getClass();
            while (c != null && c != Object.class) {
                for (final Field f : c.getDeclaredFields()) {
                    final DbUnitInstance annotation = f.getAnnotation(DbUnitInstance.class);
                    if (f.getType() == EntityManager.class && annotation != null
                        && annotation.value().equals(name) && annotation.value().isEmpty()) {
                        if (!f.isAccessible()) {
                            f.setAccessible(true);
                        }
                        try {
                            final EntityManager manager = EntityManager.class.cast(f.get(configuration.instance));
                            configuration.entityManager(name, manager);
                            return manager;
                        } catch (final IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                for (final Method m : c.getDeclaredMethods()) {
                    final DbUnitInstance annotation = m.getAnnotation(DbUnitInstance.class);
                    if (EntityManager.class == m.getReturnType() && annotation != null && m.getParameterTypes().length == 0
                        && annotation.value().equals(name) && annotation.value().isEmpty()) {
                        if (!m.isAccessible()) {
                            m.setAccessible(true);
                        }
                        try {
                            final EntityManager manager = EntityManager.class.cast(m.invoke(configuration.instance));
                            configuration.entityManager(name, manager);
                            return manager;
                        } catch (final InvocationTargetException e) {
                            throw new IllegalStateException(e.getCause());
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("No datasource available, provide either the tets instance with @DbUnitInstance on a datasource field or directly the datasource to the rule.");
    }

    private static final class Configuration {
        private DataSource dataSource;
        private Object instance;

        private PersistenceConfiguration persistenceConfiguration;
        private JpaCacheEvictionConfiguration jpaCacheEvictionConfiguration;
        private ScriptingConfiguration scriptingConfiguration;
        private DBUnitConfiguration dbUnitConfiguration;
        private final Map<String, EntityManager> entityManagers = new HashMap<String, EntityManager>();

        public void entityManager(final String name, EntityManager entityManager) {
            entityManagers.put(name, entityManager);
        }

        public void dataSource(final DataSource ds) {
            this.dataSource = ds;
        }

        public void providerInstance(final Object test) {
            this.instance = test;
        }

        public void persistenceConfiguration(final PersistenceConfiguration configuration) {
            this.persistenceConfiguration = configuration;
        }

        public void jpaCacheEvictionConfiguration(final JpaCacheEvictionConfiguration jpaCacheEvictionConfiguration) {
            this.jpaCacheEvictionConfiguration = jpaCacheEvictionConfiguration;
        }

        public void scriptingConfiguration(final ScriptingConfiguration scriptingConfiguration) {
            this.scriptingConfiguration = scriptingConfiguration;
        }

        public void dbUnitConfiguration(final DBUnitConfiguration dbUnitConfiguration) {
            this.dbUnitConfiguration = dbUnitConfiguration;
        }
    }

    private static final class State {
        private MetadataExtractor metadataExtractor;
        private PersistenceExtensionFeatureResolver persistenceExtensionFeatureResolver;
        private PersistenceExtensionScriptingFeatureResolver persistenceExtensionScriptingFeatureResolver;
        private DataSetRegister dataSetRegister;
        private DatabaseConnection databaseConnection;
        private Method method;
        private JpaCacheEvictionHandler jpaCacheEvictionHandler;

        public void metaExtractor(final MetadataExtractor metadataExtractor) {
            this.metadataExtractor = metadataExtractor;
        }

        public void persistenceExtensionFeatureResolver(final PersistenceExtensionFeatureResolver persistenceExtensionFeatureResolver) {
            this.persistenceExtensionFeatureResolver = persistenceExtensionFeatureResolver;
        }

        public void persistenceExtensionScriptingFeatureResolver(final PersistenceExtensionScriptingFeatureResolver persistenceExtensionScriptingFeatureResolver) {
            this.persistenceExtensionScriptingFeatureResolver = persistenceExtensionScriptingFeatureResolver;
        }

        public void dataSetRegister(final DataSetRegister dataSetRegister) {
            this.dataSetRegister = dataSetRegister;
        }

        public void databaseConnection(final DatabaseConnection databaseConnection) {
            this.databaseConnection = databaseConnection;
        }

        public void method(final Method testMethod) {
            this.method = testMethod;
        }

        public void jpaEvictionHandler(final JpaCacheEvictionHandler jpaHandler) {
            this.jpaCacheEvictionHandler = jpaHandler;
        }
    }
}
