package com.outpass.portal.repository;

import com.outpass.portal.model.entity.RefreshToken;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real concurrent-transaction integration test (two actual threads, two actual database
 * transactions/connections against a real JDBC-backed schema) proving the fix for the
 * refresh-token rotation TOCTOU race: {@link RefreshTokenRepository#deleteByTokenAtomic}
 * must let exactly ONE of two simultaneous callers presenting the identical token string
 * observe 1 affected row -- never both, which would mean a single-use refresh token was
 * exchanged twice.
 *
 * Deliberately hand-configures a minimal JPA context scoped to only the RefreshToken entity
 * (rather than using @DataJpaTest, which scans every @Entity in the app -- several of which
 * use MySQL-specific column types/reserved words that don't map cleanly onto H2) against a
 * dedicated in-memory H2 database. This keeps the test fast, self-contained, and independent
 * of the rest of the schema while still exercising a real database's row-level locking, which
 * is exactly the property this fix depends on.
 */
@SpringJUnitConfig(RefreshTokenRepositoryConcurrencyTest.TestConfig.class)
class RefreshTokenRepositoryConcurrencyTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // Repeated to shrink the chance of a false negative from a run where the two threads
    // happen not to overlap inside the database's row-lock window.
    @RepeatedTest(20)
    void exactlyOneConcurrentDeleteSucceedsForTheSamePresentedToken() throws Exception {
        String token = UUID.randomUUID().toString();
        TransactionTemplate setupTx = new TransactionTemplate(transactionManager);
        setupTx.executeWithoutResult(status -> refreshTokenRepository.save(RefreshToken.builder()
                .token(token).userId(1L).userType("STUDENT")
                .expiryDate(Instant.now().plusSeconds(600)).build()));

        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    TransactionTemplate threadTx = new TransactionTemplate(transactionManager);
                    Integer deleted = threadTx.execute(status -> refreshTokenRepository.deleteByTokenAtomic(token));
                    if (deleted != null && deleted == 1) {
                        successCount.incrementAndGet();
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        // The critical property under test: single-use rotation. Never zero (the token really
        // was there and one of the two racing requests must consume it) and never two (that
        // would mean the same refresh token minted two valid session pairs).
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByToken(token)).isEmpty();
    }

    @Configuration
    // includeFilters restricts Spring Data JPA repository scanning to exactly this one
    // interface -- basePackageClasses alone would still pick up every other repository in
    // the same package (e.g. AttendanceSessionRepository), which reference entities outside
    // this test's deliberately minimal RefreshToken-only persistence unit below.
    @EnableJpaRepositories(
            basePackageClasses = RefreshTokenRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RefreshTokenRepository.class))
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            // Unique DB name per test run so repeated @RepeatedTest executions never share
            // state across the pooled connections opened by each thread.
            ds.setURL("jdbc:h2:mem:refreshtoken-race-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            // Explicit managed-class-name list -- not a package scan -- so the rest of the
            // app's MySQL-specific entity mappings (which share RefreshToken's package) are
            // never involved in this test's schema.
            DefaultPersistenceUnitManager pum = new DefaultPersistenceUnitManager();
            pum.setManagedTypes(PersistenceManagedTypes.of(RefreshToken.class.getName()));
            pum.setDefaultDataSource(dataSource);
            pum.afterPropertiesSet();

            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setPersistenceUnitManager(pum);
            emf.setDataSource(dataSource);
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            emf.setJpaProperties(props);
            return emf;
        }

        @Bean
        PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean emf) {
            return new JpaTransactionManager(emf.getObject());
        }
    }
}
