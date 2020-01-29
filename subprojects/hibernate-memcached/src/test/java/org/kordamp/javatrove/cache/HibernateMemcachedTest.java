package org.kordamp.javatrove.cache;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import static java.util.Arrays.asList;
import static org.kordamp.javatrove.cache.StringUtils.padLeft;

public class HibernateMemcachedTest extends AbstractCacheTestCase {


    @Override
    protected Class<? extends Person> resolvePersonClass() {
        return HbmPerson.class;
    }

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
