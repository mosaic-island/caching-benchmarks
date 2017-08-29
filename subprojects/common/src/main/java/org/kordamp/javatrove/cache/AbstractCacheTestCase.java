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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.kordamp.javatrove.cache.StringUtils.padLeft;
import static org.kordamp.javatrove.cache.StringUtils.padRight;

/**
 * @author Andres Almiray
 */
public abstract class AbstractCacheTestCase {
    private static final int ITERATION_COUNT = 5;
    private static final int ENTITY_COUNT = 1000;
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors() / 2;
    private static final NumberFormat FORMATTER = NumberFormat.getInstance();

    private ExecutorService executorService;

    static {
        FORMATTER.setMinimumFractionDigits(6);
        FORMATTER.setMaximumFractionDigits(6);
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    @Before
    public final void setup() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("persistenceUnit");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (int i = 0; i < ENTITY_COUNT; i++) {
            em.persist(createPerson(i));
        }
        em.flush();
        em.getTransaction().commit();
        emf.close();

        executorService = Executors.newFixedThreadPool(NUMBER_OF_CORES);
    }

    @After
    public final void tearDown() throws Exception {
        executorService.shutdownNow();
        executorService.awaitTermination(2, SECONDS);
    }

    @Test
    public final void testCaches() throws Exception {
        String rootDir = System.getenv("rootDir");
        if (rootDir != null) {
            rootDir += File.separator + "build/reports";
        } else {
            rootDir = "build/reports";
        }

        List<List<Measurement>> measurements = new ArrayList<>();
        for (int iteration = 0; iteration < ITERATION_COUNT; iteration++) {
            measurements.add(executeBenchmark(iteration));
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        File xmlReport = new File(rootDir, getTestName() + ".xml");
        xmlReport.getParentFile().mkdirs();
        File csvReport = new File(rootDir, getTestName() + ".csv");

        final PrintWriter csv = new PrintWriter(new FileWriter(csvReport));
        final PrintWriter xml = new PrintWriter(new FileWriter(xmlReport));
        xml.println("<?xml version=\"\"?>");
        xml.println("<benchmark name=\"" + getTestName() + "\" time=\"" + df.format(new Date()) + "\">");

        for (int i = 0; i < measurements.size(); i++) {
            List<Measurement> list = measurements.get(i);
            System.out.println("=== Iteration " + i + " ===");
            xml.println("  <iteration index=\"" + i + "\">");
            list.forEach(e -> {
                System.out.println(e.formatted(" ", 10));
                xml.print("    <measurement key=\"" + e.key + "\"");
                xml.print(" ns=\"" + e.time + "\"");
                xml.print(" ms=\"" + (e.time / 1_000_000d) + "\"");
                xml.println("/>");
                csv.println(padRight(e.key, " ", 27) + "," + (e.time / 1_000_000d));
            });
            xml.println("  </iteration>");
        }

        xml.println("</benchmark>");
        xml.flush();
        xml.close();

        csv.flush();
        csv.close();
    }

    private List<Measurement> executeBenchmark(int iteration) throws Exception {
        System.out.println("=== Iteration " + iteration + " ===");
        List<Measurement> measurements = synchronizedList(new ArrayList<>());
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("cachedPersistenceUnit");

        EntityManager em1 = entityManagerFactory.createEntityManager();
        measurements.add(executeQueryOn(em1, "em01 [load L1C01; load L2C]"));
        em1.close();

        EntityManager em2 = entityManagerFactory.createEntityManager();
        measurements.add(executeQueryOn(em2, "em02 [hit L2C; load L1C02]"));

        measurements.add(executeQueryOn(em2, "em02 [hit l1C02]"));

        em2.clear(); // erase L1C2
        measurements.add(executeQueryOn(em2, "em02 [hit L2C; load L1C02]"));

        em2.clear(); // erase L1C2
        entityManagerFactory.getCache().evictAll(); // erase L2C
        measurements.add(executeQueryOn(em2, "em02 [load L1C02; load L2C]"));

        measurements.add(executeQueryOn(em2, "em02 [hit l1C02]"));

        int serialLimit = 10;
        for (int i = 3; i < serialLimit; i++) {
            measurements.addAll(measureHitOnCaches(entityManagerFactory, i));
        }

        executeMesuarementsConcurrently(entityManagerFactory, serialLimit, measurements);
        executeMesuarementsConcurrently(entityManagerFactory, serialLimit + NUMBER_OF_CORES, measurements);

        entityManagerFactory.getCache().evictAll();
        entityManagerFactory.close();

        return measurements;
    }

    private void executeMesuarementsConcurrently(EntityManagerFactory entityManagerFactory, final int offset, List<Measurement> measurements) throws InterruptedException {
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final CountDownLatch start = new CountDownLatch(NUMBER_OF_CORES + 1);
        final CountDownLatch end = new CountDownLatch(NUMBER_OF_CORES);
        for (int i = 0; i < NUMBER_OF_CORES; i++) {
            final int index = offset + i;
            executorService.submit(() -> {
                start.countDown();
                try {
                    start.await();
                    measurements.addAll(measureHitOnCaches(entityManagerFactory, index));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    end.countDown();
                }
            });
        }
        start.countDown();
        end.await();
    }

    private List<Measurement> measureHitOnCaches(EntityManagerFactory entityManagerFactory, int index) {
        List<Measurement> measurements = new ArrayList<>();
        EntityManager em = entityManagerFactory.createEntityManager();
        String suffix = index < 10 ? "0" + index : "" + index;
        measurements.add(executeQueryOn(em, "em" + suffix + " [hit L2C; load L1C" + suffix + "]"));
        measurements.add(executeQueryOn(em, "em" + suffix + " [hit l1C" + suffix + "]"));
        return measurements;
    }

    private Measurement executeQueryOn(EntityManager entityManager, String key) {
        Class<? extends Person> clazz = resolvePersonClass();
        boolean bl1c = entityManager.find(clazz, 1) != null;
        boolean bl2c = entityManager.getEntityManagerFactory().getCache().contains(clazz, 1);

        entityManager.getTransaction().begin();
        TypedQuery<? extends Person> query = createQuery(entityManager);
        query.setHint("org.hibernate.cacheable", "true");
        query.setHint("javax.persistence.cache.retrieveMode", "USE");
        // query.setHint("javax.persistence.cache.storeMode", "REFRESH");

        long start = System.nanoTime();
        List<? extends Person> results = query.getResultList();
        assertThat(results, hasSize(ENTITY_COUNT));
        for (Person p : results) {
            assertThat(p.getAddresses(), hasSize(2));
        }
        long end = System.nanoTime();

        entityManager.flush();
        entityManager.getTransaction().commit();

        boolean al1c = entityManager.find(clazz, 1) != null;
        boolean al2c = entityManager.getEntityManagerFactory().getCache().contains(clazz, 1);

        return new Measurement(key, end - start, bl1c, bl2c, al1c, al2c);
    }

    protected abstract Class<? extends Person> resolvePersonClass();

    protected abstract Object createPerson(int index);

    protected abstract TypedQuery<? extends Person> createQuery(EntityManager entityManager);

    protected abstract String getTestName();

    private static class Measurement {
        private final String key;
        private final long time;
        private final boolean bl1c;
        private final boolean bl2c;
        private final boolean al1c;
        private final boolean al2c;

        private Measurement(String key, long time, boolean bl1c, boolean bl2c, boolean al1c, boolean al2c) {
            this.key = key;
            this.time = time;
            this.bl1c = bl1c;
            this.bl2c = bl2c;
            this.al1c = al1c;
            this.al2c = al2c;
        }

        public String formatted(String padding, int size) {
            return padRight(key, " ", 27) + " time: " +
                padLeft(FORMATTER.format(time / 1_000_000d), padding, size) +
                " ms; " +
                " BEFORE [L1C: " + padLeft(String.valueOf(bl1c), " ", 5) + ", L2C: " + padLeft(String.valueOf(bl2c), " ", 5) + "]" +
                " AFTER  [L1C: " + padLeft(String.valueOf(al1c), " ", 5) + ", L2C: " + padLeft(String.valueOf(al2c), " ", 5) + "]";
        }
    }
}
