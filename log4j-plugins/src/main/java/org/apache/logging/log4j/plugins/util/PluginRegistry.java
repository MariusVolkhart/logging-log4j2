/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package org.apache.logging.log4j.plugins.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.plugins.Plugin;
import org.apache.logging.log4j.plugins.PluginAliases;
import org.apache.logging.log4j.plugins.processor.PluginCache;
import org.apache.logging.log4j.plugins.processor.PluginEntry;
import org.apache.logging.log4j.plugins.processor.PluginProcessor;
import org.apache.logging.log4j.plugins.processor.PluginService;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.LoaderUtil;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry singleton for PluginType maps partitioned by source type and then by category names.
 */
public class PluginRegistry {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static volatile PluginRegistry INSTANCE;
    private static final Object INSTANCE_LOCK = new Object();
    protected static final Lock STARTUP_LOCK = new ReentrantLock();

    /**
     * Contains plugins found in Log4j2Plugins.dat cache files in the main CLASSPATH.
     */
    private final AtomicReference<Map<String, List<PluginType<?>>>> pluginsByCategoryRef =
        new AtomicReference<>();

    /**
     * Contains plugins found in Log4j2Plugins.dat cache files in OSGi Bundles.
     */
    private final ConcurrentMap<Long, Map<String, List<PluginType<?>>>> pluginsByCategoryByBundleId =
        new ConcurrentHashMap<>();

    /**
     * Contains plugins found by searching for annotated classes at runtime.
     */
    private final ConcurrentMap<String, Map<String, List<PluginType<?>>>> pluginsByCategoryByPackage =
        new ConcurrentHashMap<>();

    private PluginRegistry() {
    }

    /**
     * Returns the global PluginRegistry instance.
     *
     * @return the global PluginRegistry instance.
     * @since 2.1
     */
    public static PluginRegistry getInstance() {
        PluginRegistry result = INSTANCE;
        if (result == null) {
            synchronized (INSTANCE_LOCK) {
                result = INSTANCE;
                if (result == null) {
                    INSTANCE = result = new PluginRegistry();
                }
            }
        }
        return result;
    }

    /**
     * Resets the registry to an empty state.
     */
    public void clear() {
        pluginsByCategoryRef.set(null);
        pluginsByCategoryByPackage.clear();
        pluginsByCategoryByBundleId.clear();
    }

    /**
     * @since 2.1
     */
    public Map<Long, Map<String, List<PluginType<?>>>> getPluginsByCategoryByBundleId() {
        return pluginsByCategoryByBundleId;
    }

    /**
     * @since 2.1
     */
    public Map<String, List<PluginType<?>>> loadFromMainClassLoader() {
        final Map<String, List<PluginType<?>>> existing = pluginsByCategoryRef.get();
        if (existing != null) {
            // already loaded
            return existing;
        }
        final Map<String, List<PluginType<?>>> newPluginsByCategory = decodeCacheFiles(LoaderUtil.getClassLoader());
        loadPlugins(newPluginsByCategory);

        // Note multiple threads could be calling this method concurrently. Both will do the work,
        // but only one will be allowed to store the result in the AtomicReference.
        // Return the map produced by whichever thread won the race, so all callers will get the same result.
        if (pluginsByCategoryRef.compareAndSet(null, newPluginsByCategory)) {
            return newPluginsByCategory;
        }
        return pluginsByCategoryRef.get();
    }

    /**
     * @since 2.1
     */
    public void clearBundlePlugins(final long bundleId) {
        pluginsByCategoryByBundleId.remove(bundleId);
    }

    /**
     * @since 2.1
     */
    public Map<String, List<PluginType<?>>> loadFromBundle(final long bundleId, final ClassLoader loader) {
        Map<String, List<PluginType<?>>> existing = pluginsByCategoryByBundleId.get(bundleId);
        if (existing != null) {
            // already loaded from this classloader
            return existing;
        }
        final Map<String, List<PluginType<?>>> newPluginsByCategory = decodeCacheFiles(loader);
        loadPlugins(loader, newPluginsByCategory);

        // Note multiple threads could be calling this method concurrently. Both will do the work,
        // but only one will be allowed to store the result in the outer map.
        // Return the inner map produced by whichever thread won the race, so all callers will get the same result.
        existing = pluginsByCategoryByBundleId.putIfAbsent(bundleId, newPluginsByCategory);
        if (existing != null) {
            return existing;
        }
        return newPluginsByCategory;
    }

    /**
     * Loads all the plugins in a Bundle.
     * @param categories All the categories in the bundle.
     * @since 3.0
     */
    // TODO there is a bug here. the PluginService in the repo wraps the Map it returns from getCategories() in an unmodifiableMap.
    //   This function tries to mutate that map.
    public void loadFromBundle(Map<String, List<PluginType<?>>> categories, Long bundleId) {
        pluginsByCategoryByBundleId.put(bundleId, categories);
        for (Map.Entry<String, List<PluginType<?>>> entry: categories.entrySet()) {
            if (!categories.containsKey(entry.getKey())) {

                categories.put(entry.getKey(), new LinkedList<>());
            }
            categories.get(entry.getKey()).addAll(entry.getValue());
        }
    }

    /**
     * @since 3.0
     */
    public void loadPlugins(Map<String, List<PluginType<?>>> map) {
        for (ClassLoader classLoader : LoaderUtil.getClassLoaders()) {
            try {
                loadPlugins(classLoader, map);
            } catch (Throwable ex) {
                LOGGER.debug("Unable to retrieve provider from ClassLoader {}", classLoader, ex);
            }
        }
    }

    /**
     * @since 3.0
     */
    public void loadPlugins(ClassLoader classLoader, Map<String, List<PluginType<?>>> categories) {
        final long startTime = System.nanoTime();
        final ServiceLoader<PluginService> serviceLoader = ServiceLoader.load(PluginService.class, classLoader);
        int pluginCount = 0;
        for (final PluginService pluginService : serviceLoader) {
            pluginService.contributePlugins(categories);
        }
        final int numPlugins = pluginCount;
        LOGGER.debug(() -> {
            final long endTime = System.nanoTime();
            StringBuilder sb = new StringBuilder("Took ");
            final DecimalFormat numFormat = new DecimalFormat("#0.000000");
            sb.append(numFormat.format((endTime - startTime) * 1e-9));
            sb.append(" seconds to load ").append(numPlugins);
            sb.append(" plugins from ").append(classLoader);
            return sb.toString();
        });
    }

    private Map<String, List<PluginType<?>>> decodeCacheFiles(final ClassLoader loader) {
        final long startTime = System.nanoTime();
        final PluginCache cache = new PluginCache();
        try {
            final Enumeration<URL> resources = loader.getResources(PluginProcessor.PLUGIN_CACHE_FILE);
            if (resources == null) {
                LOGGER.info("Plugin preloads not available from class loader {}", loader);
            } else {
                cache.loadCacheFiles(resources);
            }
        } catch (final IOException ioe) {
            LOGGER.warn("Unable to preload plugins", ioe);
        }
        final Map<String, List<PluginType<?>>> newPluginsByCategory = new HashMap<>();
        int pluginCount = 0;
        for (final Map.Entry<String, Map<String, PluginEntry>> outer : cache.getAllCategories().entrySet()) {
            final String categoryLowerCase = outer.getKey();
            final List<PluginType<?>> types = new ArrayList<>(outer.getValue().size());
            newPluginsByCategory.put(categoryLowerCase, types);
            for (final Map.Entry<String, PluginEntry> inner : outer.getValue().entrySet()) {
                final PluginEntry entry = inner.getValue();
                final String className = entry.getClassName();
                try {
                    final Class<?> clazz = loader.loadClass(className);
                    final PluginType<?> type = new EntryPluginType<>(entry, clazz, entry.getName());
                    types.add(type);
                    ++pluginCount;
                } catch (final ClassNotFoundException e) {
                    LOGGER.info("Plugin [{}] could not be loaded due to missing classes.", className, e);
                } catch (final LinkageError e) {
                    LOGGER.info("Plugin [{}] could not be loaded due to linkage error.", className, e);
                }
            }
        }
        final int numPlugins = pluginCount;
        LOGGER.debug(() -> {
            final long endTime = System.nanoTime();
            StringBuilder sb = new StringBuilder("Took ");
            final DecimalFormat numFormat = new DecimalFormat("#0.000000");
            sb.append(numFormat.format((endTime - startTime) * 1e-9));
            sb.append(" seconds to load ").append(numPlugins);
            sb.append(" plugins from ").append(loader);
            return sb.toString();
        });
        return newPluginsByCategory;
    }

    /**
     * @since 2.1
     */
    public Map<String, List<PluginType<?>>> loadFromPackage(final String pkg) {
        if (Strings.isBlank(pkg)) {
            // happens when splitting an empty string
            return Collections.emptyMap();
        }
        Map<String, List<PluginType<?>>> existing = pluginsByCategoryByPackage.get(pkg);
        if (existing != null) {
            // already loaded this package
            return existing;
        }

        final long startTime = System.nanoTime();
        final ResolverUtil resolver = new ResolverUtil();
        final ClassLoader classLoader = LoaderUtil.getClassLoader();
        if (classLoader != null) {
            resolver.setClassLoader(classLoader);
        }
        resolver.findInPackage(new PluginTest(), pkg);

        final Map<String, List<PluginType<?>>> newPluginsByCategory = new HashMap<>();
        for (final Class<?> clazz : resolver.getClasses()) {
            final Plugin plugin = clazz.getAnnotation(Plugin.class);
            final String categoryLowerCase = plugin.category().toLowerCase();
            List<PluginType<?>> list = newPluginsByCategory.get(categoryLowerCase);
            if (list == null) {
                newPluginsByCategory.put(categoryLowerCase, list = new ArrayList<>());
            }
            final boolean usePluginElementType = !Plugin.EMPTY.equals(plugin.elementType());
            final String pluginName = plugin.name();
            final String className = clazz.getName();
            final boolean printable = plugin.printObject();
            final boolean defer = plugin.deferChildren();
            final String category = plugin.category();
            String elementName = usePluginElementType ? plugin.elementType() : pluginName;
            final PluginEntry mainEntry = new PluginEntry(
                    pluginName.toLowerCase(),
                    className,
                    pluginName,
                    printable,
                    defer,
                    category
            );
            final PluginType<?> mainType = new EntryPluginType<>(mainEntry, clazz, elementName);
            list.add(mainType);
            final PluginAliases pluginAliases = clazz.getAnnotation(PluginAliases.class);
            if (pluginAliases != null) {
                for (String alias : pluginAliases.value()) {
                    alias = alias.trim();
                    if (!usePluginElementType) {
                        elementName = alias;
                    }
                    final PluginEntry aliasEntry = new PluginEntry(
                            alias.toLowerCase(),
                            className,
                            pluginName,
                            printable,
                            defer,
                            category
                    );
                    final PluginType<?> aliasType = new EntryPluginType<>(aliasEntry, clazz, elementName);
                    list.add(aliasType);
                }
            }
        }
        LOGGER.debug(() -> {
            final long endTime = System.nanoTime();
            StringBuilder sb = new StringBuilder("Took ");
            final DecimalFormat numFormat = new DecimalFormat("#0.000000");
            sb.append(numFormat.format((endTime - startTime) * 1e-9));
            sb.append(" seconds to load ").append(resolver.getClasses().size());
            sb.append(" plugins from package ").append(pkg);
            return sb.toString();
        });

        // Note multiple threads could be calling this method concurrently. Both will do the work,
        // but only one will be allowed to store the result in the outer map.
        // Return the inner map produced by whichever thread won the race, so all callers will get the same result.
        existing = pluginsByCategoryByPackage.putIfAbsent(pkg, newPluginsByCategory);
        if (existing != null) {
            return existing;
        }
        return newPluginsByCategory;
    }

    /**
     * A Test that checks to see if each class is annotated with the 'Plugin' annotation. If it
     * is, then the test returns true, otherwise false.
     *
     * @since 2.1
     */
    public static class PluginTest implements ResolverUtil.Test {
        @Override
        public boolean matches(final Class<?> type) {
            return type != null && type.isAnnotationPresent(Plugin.class);
        }

        @Override
        public String toString() {
            return "annotated with @" + Plugin.class.getSimpleName();
        }

        @Override
        public boolean matches(final URI resource) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean doesMatchClass() {
            return true;
        }

        @Override
        public boolean doesMatchResource() {
            return false;
        }
    }
}
