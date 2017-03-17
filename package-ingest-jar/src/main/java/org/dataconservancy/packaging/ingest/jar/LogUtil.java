/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest.jar;

import static java.util.stream.Stream.concat;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Adjust logging levels based on environment variables or system properties.
 * <p>
 * A system property or environment variable of the form <code>LOG.loggerName=LEVEL</code> will set the logger with
 * name <code>loggerName</code> to <code>LEVEL</code>. For example, <code>LOG.org.fcrepo=DEBUG</code>
 * </p>
 *
 * @author apb@jhu.edu
 */
abstract class LogUtil {

    static final String PREFIX = "LOG.";

    static void adjustLogLevels() {
        concat(System.getenv().keySet().stream(), System.getProperties().stringPropertyNames().stream())
                .filter(key -> key.startsWith(PREFIX))
                .forEach(LogUtil::updateLogger);
    }

    static void updateLogger(final String spec) {
        final String logger = spec.substring(PREFIX.length());
        final Level level = Level.toLevel(System.getenv().getOrDefault(spec, System.getProperty(spec, "DEBUG")));
        ((Logger) LoggerFactory.getLogger(logger)).setLevel(level);
    }

}
