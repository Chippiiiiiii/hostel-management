package com.outpass.portal.service;

import com.outpass.portal.dto.request.OutpassRequest;
import com.outpass.portal.dto.response.OutpassResponse;
import com.outpass.portal.model.entity.Building;
import com.outpass.portal.model.entity.Outpass;
import com.outpass.portal.model.entity.Room;
import com.outpass.portal.model.entity.RoomAllocation;
import com.outpass.portal.model.entity.Student;
import com.outpass.portal.repository.OutpassRepository;
import com.outpass.portal.repository.StudentRepository;
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
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
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
 * transactions/connections, against the real {@link OutpassService} bean -- not a mock) proving
 * the fix for the duplicate/overlapping-active-outpass defense-in-depth item: two simultaneous
 * createOutpass requests from the same student must not both bypass the "no active outpass yet"
 * check and both succeed. Uses the same minimal hand-rolled JPA context pattern as
 * RefreshTokenRepositoryConcurrencyTest, scoped to only Student + Outpass, for the same reasons
 * (several other entities use MySQL-specific types/reserved words that don't map onto H2).
 */
@SpringJUnitConfig(OutpassCreationConcurrencyTest.TestConfig.class)
class OutpassCreationConcurrencyTest {

    @Autowired
    private OutpassService outpassService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private OutpassRepository outpassRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private OutpassRequest validRequest() {
        OutpassRequest request = new OutpassRequest();
        request.setReason("Family function");
        request.setPlaceOfVisit("Home");
        request.setDate(LocalDateTime.now().plusHours(1));
        request.setReturnDate(LocalDateTime.now().plusDays(1));
        request.setNoOfDays(1);
        request.setContactNumber("9000000000");
        request.setParentNumber("9000000001");
        return request;
    }

    // Repeated to shrink the chance of a false negative from a run where the two threads
    // happen not to overlap inside the database's row-lock window.
    @RepeatedTest(15)
    void exactlyOneConcurrentCreateOutpassSucceedsForTheSameStudent() throws Exception {
        TransactionTemplate setupTx = new TransactionTemplate(transactionManager);
        Long studentId = setupTx.execute(status -> studentRepository.save(Student.builder()
                .name("Race Student").email("race-" + UUID.randomUUID() + "@x.com")
                .passwordHash("hashed").rollNo("R1").department("CT")
                .hostel("Building A").roomNumber("101")
                .contactNumber("9000000000").parentNumber("9000000001")
                .gender("BOY").build()).getId());

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
                    try {
                        OutpassResponse response = outpassService.createOutpass(studentId, validRequest());
                        if (response != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (RuntimeException expectedForTheLoser) {
                        // "You already have an active outpass..." -- expected for the request
                        // that loses the race.
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(successCount.get()).isEqualTo(1);
        Long finalStudentId = studentId;
        Long outpassCount = setupTx.execute(status ->
                (long) outpassRepository.findByStudentIdOrderByCreatedAtDesc(finalStudentId).size());
        assertThat(outpassCount).isEqualTo(1L);
    }

    @Configuration
    @EnableJpaRepositories(
            basePackageClasses = OutpassRepository.class,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {OutpassRepository.class, StudentRepository.class}))
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() throws Exception {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:outpass-race-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            // Student.profilePicture uses columnDefinition="LONGTEXT" (a valid MySQL type);
            // H2's MySQL compatibility mode doesn't automatically alias it, so declare it as
            // a VARCHAR domain up front rather than changing the entity mapping for this test.
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE DOMAIN IF NOT EXISTS LONGTEXT AS VARCHAR");
            }
            return ds;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            DefaultPersistenceUnitManager pum = new DefaultPersistenceUnitManager();
            // StudentRepository declares a query referencing RoomAllocation (which in turn
            // references Room, which references Building) -- Spring Data validates every
            // declared repository method at startup regardless of whether this test calls it,
            // so the whole small FK cluster must be registered even though this test only
            // exercises Student + Outpass.
            pum.setManagedTypes(PersistenceManagedTypes.of(
                    Student.class.getName(), Outpass.class.getName(),
                    RoomAllocation.class.getName(), Room.class.getName(), Building.class.getName()));
            pum.setDefaultDataSource(dataSource);
            pum.afterPropertiesSet();

            LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
            emf.setPersistenceUnitManager(pum);
            emf.setDataSource(dataSource);
            emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            // Student.year collides with an H2 reserved word; quoting every identifier lets
            // the same entity mappings that generate valid MySQL DDL in production also
            // generate valid H2 DDL here.
            props.setProperty("hibernate.globally_quoted_identifiers", "true");
            emf.setJpaProperties(props);
            return emf;
        }

        @Bean
        PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean emf) {
            return new JpaTransactionManager(emf.getObject());
        }

        @Bean
        OutpassService outpassService(OutpassRepository outpassRepository, StudentRepository studentRepository) {
            return new OutpassService(outpassRepository, studentRepository);
        }
    }
}
