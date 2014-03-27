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
package org.apache.logging.log4j.core.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEventListener;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.xml.XMLConfiguration;
import org.apache.logging.log4j.core.config.xml.XMLConfigurationFactory;

/**
 * Abstract socket server for TCP and UDP implementations.
 */
public abstract class AbstractSocketServer extends LogEventListener {

    /**
     * Factory that creates a Configuration for the server.
     */
    protected static class ServerConfigurationFactory extends XMLConfigurationFactory {

        private final String path;

        public ServerConfigurationFactory(final String path) {
            this.path = path;
        }

        @Override
        public Configuration getConfiguration(final String name, final URI configLocation) {
            if (path != null && path.length() > 0) {
                File file = null;
                ConfigurationSource source = null;
                try {
                    file = new File(path);
                    final FileInputStream is = new FileInputStream(file);
                    source = new ConfigurationSource(is, file);
                } catch (final FileNotFoundException ex) {
                    // Ignore this error
                }
                if (source == null) {
                    try {
                        final URL url = new URL(path);
                        source = new ConfigurationSource(url.openStream(), path);
                    } catch (final MalformedURLException mue) {
                        // Ignore this error
                    } catch (final IOException ioe) {
                        // Ignore this error
                    }
                }

                try {
                    if (source != null) {
                        return new XMLConfiguration(source);
                    }
                } catch (final Exception ex) {
                    // Ignore this error.
                }
                System.err.println("Unable to process configuration at " + path + ", using default.");
            }
            return super.getConfiguration(name, configLocation);
        }
    }
    
    protected final Logger logger;

    protected static final int MAX_PORT = 65534;

    private volatile boolean active = true;

    public AbstractSocketServer(int port) {
        this.logger = LogManager.getLogger(this.getClass().getName() + '.' + port);
    }

    protected boolean isActive() {
        return this.active;
    }

    protected void setActive(boolean isActive) {
        this.active = isActive;
    }

}
