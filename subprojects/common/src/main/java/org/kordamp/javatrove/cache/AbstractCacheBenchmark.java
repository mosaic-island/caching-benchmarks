/*
 * Copyright 2017 Andres Almiray
 *
 * This file is part of JavaTrove Examples
 *
 * JavaTrove Examples is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JavaTrove Examples is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaTrove Examples. If not, see <http://www.gnu.org/licenses/>.
 */
package org.kordamp.javatrove.cache;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Andres Almiray
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 20)
@Measurement(iterations = 10)
@Fork(5)
public abstract class AbstractCacheBenchmark {
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors() / 2;
    private static final NumberFormat FORMATTER = NumberFormat.getInstance();

    private ExecutorService executorService;

    @Param({"1000", "5000"})
    private int entityCount;

    static {
        FORMATTER.setMinimumFractionDigits(6);
        FORMATTER.setMaximumFractionDigits(6);
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    @Setup
    public final void setup() throws Exception {
        executorService = Executors.newFixedThreadPool(NUMBER_OF_CORES);
        setupDataset(entityCount);
    }

    @TearDown
    public final void tearDown() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(2, SECONDS);
    }

    private void setupDataset(int entityCount) {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(getBaseName() + "-persistenceUnit");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (int i = 0; i < entityCount; i++) {
            em.persist(createPerson(i));
        }
        em.flush();
        em.getTransaction().commit();
        emf.close();
    }

    @Benchmark
    public final void _01_test_read_only(Blackhole bh) throws Exception {
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(getTestName() + "-cachedPersistenceUnit");

        EntityManager em1 = entityManagerFactory.createEntityManager();
        executeQueryOn(em1, entityCount, bh);
        em1.close();

        EntityManager em2 = entityManagerFactory.createEntityManager();
        executeQueryOn(em2, entityCount, bh);

        executeQueryOn(em2, entityCount, bh);

        em2.clear(); // erase L1C2
        executeQueryOn(em2, entityCount, bh);

        em2.clear(); // erase L1C2
        entityManagerFactory.getCache().evictAll(); // erase L2C
        executeQueryOn(em2, entityCount, bh);

        executeQueryOn(em2, entityCount, bh);

        for (int i = 3; i < 10; i++) {
            measureHitOnCaches(entityManagerFactory, entityCount, bh);
        }

        executeMeasurementsConcurrently(entityManagerFactory, entityCount, bh);
        executeMeasurementsConcurrently(entityManagerFactory, entityCount, bh);

        entityManagerFactory.getCache().evictAll();
        entityManagerFactory.close();
    }

    private void executeMeasurementsConcurrently(EntityManagerFactory entityManagerFactory, int entityCount, Blackhole bh) throws Exception {
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final CountDownLatch start = new CountDownLatch(NUMBER_OF_CORES + 1);
        final CountDownLatch end = new CountDownLatch(NUMBER_OF_CORES);
        for (int i = 0; i < NUMBER_OF_CORES; i++) {
            executorService.submit(() -> {
                start.countDown();
                try {
                    start.await();
                    measureHitOnCaches(entityManagerFactory, entityCount, bh);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    end.countDown();
                }
            });
        }
        start.countDown();
        end.await();

        if (!errors.isEmpty()) {
            IllegalStateException iae = new IllegalStateException("There are " + errors.size() + " errors!");
            errors.forEach(iae::addSuppressed);
            throw iae;
        }
    }

    private void measureHitOnCaches(EntityManagerFactory entityManagerFactory, int entityCount, Blackhole bh) {
        EntityManager em = entityManagerFactory.createEntityManager();
        executeQueryOn(em, entityCount, bh);
        executeQueryOn(em, entityCount, bh);
        em.close();
    }

    private void executeQueryOn(EntityManager entityManager, int entityCount, Blackhole bh) {
        entityManager.getTransaction().begin();
        TypedQuery<? extends Person> query = createQuery(entityManager);
        query.setHint("org.hibernate.cacheable", "true");
        query.setHint("javax.persistence.cache.retrieveMode", "USE");
        // query.setHint("javax.persistence.cache.storeMode", "REFRESH");

        List<? extends Person> results = query.getResultList();
        bh.consume(results);
        assertThat(results, hasSize(entityCount));
        for (Person p : results) {
            assertThat(p.getAddresses(), hasSize(2));
        }

        entityManager.flush();
        entityManager.getTransaction().commit();
    }

    protected abstract Object createPerson(int index);

    protected abstract TypedQuery<? extends Person> createQuery(EntityManager entityManager);

    protected abstract String getTestName();

    protected abstract String getBaseName();
}
