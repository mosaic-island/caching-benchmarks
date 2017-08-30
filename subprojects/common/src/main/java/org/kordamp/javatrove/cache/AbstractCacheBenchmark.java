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
import org.openjdk.jmh.annotations.Level;
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
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.openjdk.jmh.annotations.Level.Iteration;

/**
 * @author Andres Almiray
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 30)
@Measurement(iterations = 10)
@Fork(5)
public abstract class AbstractCacheBenchmark {
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors() / 2;
    private static final NumberFormat FORMATTER = NumberFormat.getInstance();

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @Param({"1000", "10000"})
    private int entityCount;

    static {
        FORMATTER.setMinimumFractionDigits(6);
        FORMATTER.setMaximumFractionDigits(6);
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    @Setup
    public final void setupTrial() throws Exception {
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

    @Setup(Iteration)
    public final void setupIteration() {
        entityManagerFactory = Persistence.createEntityManagerFactory(getTestName() + "-cachedPersistenceUnit");

        // load L2C
        EntityManager em = entityManagerFactory.createEntityManager();
        executeQueryOn(null, em, entityCount);
        em.close();

        entityManager = entityManagerFactory.createEntityManager();
    }

    @TearDown(Iteration)
    public final void tearDownIteration() {
        entityManager.close();

        entityManagerFactory.getCache().evictAll();
        entityManagerFactory.close();
    }

    @Benchmark
    public final void _01_hit_L2C(Blackhole bh) throws Exception {
        executeQueryOn(bh, entityManager, entityCount);
    }

    private void executeQueryOn(Blackhole bh, EntityManager entityManager, int entityCount) {
        entityManager.getTransaction().begin();
        TypedQuery<? extends Person> query = createQuery(entityManager);
        query.setHint("org.hibernate.cacheable", "true");
        query.setHint("javax.persistence.cache.retrieveMode", "USE");
        // query.setHint("javax.persistence.cache.storeMode", "REFRESH");

        List<? extends Person> results = query.getResultList();
        if (bh != null) {bh.consume(results);}
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
