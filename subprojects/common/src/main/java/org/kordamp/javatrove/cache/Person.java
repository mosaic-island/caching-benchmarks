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

import java.io.Serializable;
import java.util.List;

/**
 * @author Andres Almiray
 */
public interface Person extends Serializable {
    Integer getId();

    void setId(Integer id);

    String getName();

    void setName(String name);

    String getLastname();

    void setLastname(String lastname);

    List<Address> getAddresses();

    void setAddresses(List<Address> addresses);
}
