package org.kordamp.javatrove.cache;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import static java.util.Arrays.asList;
import static org.kordamp.javatrove.cache.StringUtils.padLeft;

public class HibernateMemcachedBenchmark extends AbstractCacheBenchmark {

    @Override
    protected Object createPerson(int index) {
        String suffix = padLeft(String.valueOf(index), "0", 5);
        return new HbmPerson("name_" + suffix,
                "lastname_" + suffix,
                asList(new HbmAddress("home_address_" + suffix + "_001"), new HbmAddress("office_address_" + suffix + "_002")));
    }

    @Override
    protected TypedQuery<? extends Person> createQuery(EntityManager entityManager) {
        return entityManager.createQuery("select p from HbmPerson p", Person.class);
    }

    @Override
    protected String getTestName() {
        return "hibernate-memcached";
    }

    @Override
    protected String getBaseName() {
        return "hibernate";
    }
}


//# Run complete. Total time: 00:40:20
//
//        Benchmark                                (entityCount)  Mode  Cnt     Score     Error  Units
//        HibernateBenchmark._01_hit_L2C                     100    ss   25     6.270 ±   1.072  ms/op
//        HibernateBenchmark._01_hit_L2C                    1000    ss   25    26.119 ±   7.483  ms/op
//        HibernateEhcacheBenchmark._01_hit_L2C              100    ss   25     3.260 ±   0.478  ms/op
//        HibernateEhcacheBenchmark._01_hit_L2C             1000    ss   25    15.380 ±   1.655  ms/op
//        HibernateMemcachedBenchmark._01_hit_L2C            100    ss   25   308.069 ±  43.320  ms/op
//        HibernateMemcachedBenchmark._01_hit_L2C           1000    ss   25  2728.575 ±  98.583  ms/op
//        HibernateRedissonBenchmark._01_hit_L2C             100    ss   25  1001.791 ±  39.386  ms/op
//        HibernateRedissonBenchmark._01_hit_L2C            1000    ss   25  9808.906 ± 330.939  ms/op
