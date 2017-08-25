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

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.transaction.Transactional;

/**
 * @author Andres Almiray
 */
@Entity
@Table(name = "addresses")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Transactional
public class HbmAddress implements Address {
    private static final long serialVersionUID = 6600041802974891049L;

    @Id
    @Column(nullable = false, name = "address_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String address;

    @ManyToOne(targetEntity = HbmPerson.class)
    @JoinColumn(name = "person_id")
    private Person person;

    public HbmAddress() {
    }

    public HbmAddress(String address) {
        setAddress(address);
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public Person getPerson() {
        return person;
    }

    @Override
    public void setPerson(Person person) {
        this.person = person;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Address{");
        sb.append("id=").append(id);
        sb.append(", address='").append(address).append('\'');
        sb.append(", person=").append(person.getId());
        sb.append('}');
        return sb.toString();
    }
}
