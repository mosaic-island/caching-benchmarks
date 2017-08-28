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

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.kordamp.javatrove.cache.StringUtils.padLeft;
import static org.kordamp.javatrove.cache.StringUtils.padRight;

/**
 * @author Andres Almiray
 */
public abstract class AbstractCacheTestCase {
    private static final int PERSON_COUNT = 1000;
    private static final NumberFormat FORMATTER = NumberFormat.getInstance();

    static {
        FORMATTER.setMinimumFractionDigits(6);
        FORMATTER.setMaximumFractionDigits(6);
    }

    @Before
    public final void setup() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("persistenceUnit");
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        for (int i = 0; i < PERSON_COUNT; i++) {
            em.persist(createPerson(i));
        }
        em.flush();
        em.getTransaction().commit();
        emf.close();
    }

    @Test
    public final void testCaches() throws Exception {
        String rootDir = System.getenv("rootDir");
        if (rootDir != null) {
            rootDir += File.separator + "build/reports";
        } else {
            rootDir = "build/reports";
        }

        List<List<Event>> events = new ArrayList<>();
        for (int iteration = 0; iteration < 50; iteration++) {
            executeBenchmark(iteration, events);
        }

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        File xmlReport = new File(rootDir, getTestName() + ".xml");
        xmlReport.getParentFile().mkdirs();
        File csvReport = new File(rootDir, getTestName() + ".csv");

        final PrintWriter csv = new PrintWriter(new FileWriter(csvReport));
        final PrintWriter xml = new PrintWriter(new FileWriter(xmlReport));
        xml.println("<?xml version=\"\"?>");
        xml.println("<benchmark name=\"" + getTestName() + "\" time=\"" + df.format(new Date()) + "\">");

        for (int i = 0; i < events.size(); i++) {
            List<Event> list = events.get(i);
            System.out.println("=== Iteration " + i + " ===");
            xml.println("  <iteration index=\"" + i + "\">");
            list.forEach(e -> {
                System.out.println(e.formatted(" ", 10));
                xml.print("    <event key=\"" + e.key + "\"");
                xml.print(" ns=\"" + e.time + "\"");
                xml.print(" ms=\"" + (e.time / 1_000_000d) + "\"");
                xml.println("/>");
                csv.println(padRight(e.key, " ", 25) + "," + (e.time / 1_000_000d));
            });
            xml.println("  </iteration>");
        }

        xml.println("</benchmark>");
        xml.flush();
        xml.close();

        csv.flush();
        csv.close();
    }

    private void executeBenchmark(int iteration, List<List<Event>> events) {
        System.out.println("=== Iteration " + iteration + " ===");
        List<Event> list = new ArrayList<>();
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("cachedPersistenceUnit");

        EntityManager em1 = entityManagerFactory.createEntityManager();
        executeQueryOn(em1, "em1 [load L1C1; load L2C]", list);
        em1.close();

        EntityManager em2 = entityManagerFactory.createEntityManager();
        executeQueryOn(em2, "em2 [hit L2C; load L1C2]", list);

        executeQueryOn(em2, "em2 [hit l1C2]", list);

        em2.clear(); // erase L1C2
        executeQueryOn(em2, "em2 [hit L2C; load L1C2]", list);

        em2.clear(); // erase L1C2
        entityManagerFactory.getCache().evictAll(); // erase L2C
        executeQueryOn(em2, "em2 [load L1C2; load L2C]", list);

        List<? extends Person> people = executeQueryOn(em2, "em2 [hit l1C2]", list);
        List<Integer> ids = people.stream().map(Person::getId).collect(toList());

        for (int i = 3; i < 10; i++) {
            EntityManager em = entityManagerFactory.createEntityManager();
            // loaOn(em, "em" + i + " [hit L2C; load L1C" + i + "]", ids, list);
            executeQueryOn(em, "em" + i + " [hit L2C; load L1C" + i + "]", list);
            executeQueryOn(em, "em" + i + " [hit l1C" + i + "]", list);
        }

        events.add(list);

        entityManagerFactory.getCache().evictAll();
        entityManagerFactory.close();
    }

    protected void loaOn(EntityManager em, String key, List<Integer> ids, List<Event> events) {
        Class<? extends Person> clazz = resolvePersonClass();
        long start = System.nanoTime();
        ids.forEach(id -> {
            Person p = em.find(clazz, id);
            assertThat(p.getAddresses(), hasSize(2));
        });
        long end = System.nanoTime();
        events.add(new Event(key, end - start, true, true, true, true));
    }

    private List<? extends Person> executeQueryOn(EntityManager entityManager, String key, List<Event> events) {
        Class<? extends Person> clazz = resolvePersonClass();
        boolean bl1c = entityManager.find(clazz, 1) != null;
        boolean bl2c = entityManager.getEntityManagerFactory().getCache().contains(clazz, 1);

        entityManager.getTransaction().begin();
        TypedQuery<? extends Person> query = createQuery(entityManager);
        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        query.setHint("org.hibernate.cacheable", "true");

        long start = System.nanoTime();
        List<? extends Person> results = query.getResultList();
        assertThat(results, hasSize(PERSON_COUNT));
        for (Person p : results) {
            assertThat(p.getAddresses(), hasSize(2));
        }
        long end = System.nanoTime();

        entityManager.flush();
        entityManager.getTransaction().commit();

        boolean al1c = entityManager.find(clazz, 1) != null;
        boolean al2c = entityManager.getEntityManagerFactory().getCache().contains(clazz, 1);

        events.add(new Event(key, end - start, bl1c, bl2c, al1c, al2c));

        return results;
    }

    protected abstract Class<? extends Person> resolvePersonClass();

    protected abstract Object createPerson(int index);

    protected abstract TypedQuery<? extends Person> createQuery(EntityManager entityManager);

    protected abstract String getTestName();

    private static class Event {
        private final String key;
        private final long time;
        private final boolean bl1c;
        private final boolean bl2c;
        private final boolean al1c;
        private final boolean al2c;

        private Event(String key, long time, boolean bl1c, boolean bl2c, boolean al1c, boolean al2c) {
            this.key = key;
            this.time = time;
            this.bl1c = bl1c;
            this.bl2c = bl2c;
            this.al1c = al1c;
            this.al2c = al2c;
        }

        public String formatted(String padding, int size) {
            return padRight(key, " ", 25) + " time: " +
                padLeft(FORMATTER.format(time / 1_000_000d), padding, size) +
                " ms; " +
                " BEFORE [L1C: " + padLeft(String.valueOf(bl1c), " ", 5) + ", L2C: " + padLeft(String.valueOf(bl2c), " ", 5) + "]" +
                " AFTER  [L1C: " + padLeft(String.valueOf(al1c), " ", 5) + ", L2C: " + padLeft(String.valueOf(al2c), " ", 5) + "]";
        }
    }
}
