/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.packaging.ingest.integration;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

/**
 * @author apb@jhu.edu
 */
public class JarRunner {

    private final ProcessBuilder builder;

    private Logger log;

    public static JarRunner jar(final String jarPath, final String... args) {
        return new JarRunner(jarPath, args);
    }

    /**
     * Set an environment variable.
     *
     * @param key The environment variable name
     * @param value The value
     * @return configured JarRunner.
     */
    public JarRunner withEnv(final String key, final String value) {
        builder.environment().put(key, value);
        return this;
    }

    /**
     * Log process output to the given logger.
     * <p>
     * Each line of stdin or stdout will be logged at the INFO level.
     * </p>
     *
     * @param log Logger.
     * @return configured JarRunner.
     */
    public JarRunner logOutput(final Logger log) {
        this.log = log;
        builder.redirectErrorStream(true);
        return this;
    }

    private JarRunner(final String jarPath, final String... args) {
        final ArrayList<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(jarPath);
        cmd.addAll(Arrays.asList(args));
        this.builder = new ProcessBuilder(cmd.toArray(new String[0]));
    }

    public Process start() throws IOException {
        final Process proc = builder.start();

        if (log != null) {

            Executors.newSingleThreadExecutor().execute(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(),
                        UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info(line);
                    }

                } catch (final IOException e) {
                    log.warn("Error handling process io", e);
                }
            });
        }

        return proc;
    }
}
