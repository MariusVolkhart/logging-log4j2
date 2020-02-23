/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.plugins.util;

public final class AdHocPluginType<T> implements PluginType<T> {

    private final Class<T> clazz;
    private final String name;
    private final String key;
    private final boolean printable;
    private final boolean defer;
    private final String category;

    public AdHocPluginType(String key, Class<T> clazz, String name, boolean printable, boolean defer, String category) {
        this.clazz = clazz;
        this.name = name;
        this.key = key;
        this.printable = printable;
        this.defer = defer;
        this.category = category;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Class<T> getPluginClass() {
        return clazz;
    }

    @Override
    public String getElementName() {
        return name;
    }

    @Override
    public boolean isObjectPrintable() {
        return printable;
    }

    @Override
    public boolean isDeferChildren() {
        return defer;
    }

    @Override
    public String getCategory() {
        return category;
    }
}
