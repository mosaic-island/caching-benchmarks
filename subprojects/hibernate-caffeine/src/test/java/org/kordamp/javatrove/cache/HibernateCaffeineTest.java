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

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import static java.util.Arrays.asList;
import static org.kordamp.javatrove.cache.StringUtils.padLeft;

/**
 * @author Andres Almiray
 */
public class HibernateCaffeineTest extends AbstractCacheTestCase {
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
        return "hibernate-caffeine";
    }
}
