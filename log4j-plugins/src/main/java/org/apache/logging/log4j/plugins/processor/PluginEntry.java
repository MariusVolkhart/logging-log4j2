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

package org.apache.logging.log4j.plugins.processor;

import java.io.Serializable;

/**
 * Memento object for storing a plugin entry to a cache file.
 */
public class PluginEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String key;
    private final String className;
    private final String name;
    private final boolean printable;
    private final boolean defer;
    private final String category;

    public PluginEntry(String key, String className, String name, boolean printable, boolean defer, String category) {
        this.key = key;
        this.className = className;
        this.name = name;
        this.printable = printable;
        this.defer = defer;
        this.category = category;
    }

    public String getKey() {
        return key;
    }

    public String getClassName() {
        return className;
    }

    public String getName() {
        return name;
    }

    public boolean isPrintable() {
        return printable;
    }

    public boolean isDefer() {
        return defer;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public String toString() {
        return "PluginEntry [key=" + key + ", className=" + className + ", name=" + name + ", printable=" + printable
                + ", defer=" + defer + ", category=" + category + "]";
    }
}
