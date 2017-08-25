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

/**
 * @author Andres Almiray
 */
public class StringUtils {
    public static String padLeft(String str, String padding, int size) {
        return str.length() >= size ? str : multiply(padding, size - str.length()) + str;
    }

    public static String padRight(String str, String padding, int size) {
        return str.length() >= size ? str : str + multiply(padding, size - str.length());
    }

    public static String multiply(String str, int times) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < times; i++) {
            b.append(str);
        }
        return b.toString();
    }
}
