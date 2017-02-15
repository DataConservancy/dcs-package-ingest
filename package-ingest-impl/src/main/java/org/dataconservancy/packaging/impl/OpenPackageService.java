/*
 * Copyright 2016 Johns Hopkins University
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

package org.dataconservancy.packaging.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for opening up a package file for ingest.
 * <p>
 * This code is duplicated from OpenPackageServiceImpl in the Package Tool codebase.
 * </p>
 *
 * @author mpatton@jhu.edu
 */
public class OpenPackageService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenPackageService.class);

    /**
     * Extract contents of an archive.
     *
     * @param dest_dir Destination to write archive content.
     * @param is Archive file.
     * @return Name of package base directory in dest_dir
     * @throws ArchiveException if there is an error creating the ArchiveInputStream
     * @throws IOException if there is more than one package root
     */
    private String extract(final File dest_dir, final InputStream i) throws ArchiveException, IOException {

        final ArchiveInputStream ais = archiveStream(i);
        ArchiveEntry entry;

        String archive_base = null;
        while ((entry = ais.getNextEntry()) != null) {

            final File file = extract(dest_dir, entry, ais);
            LOG.info("Extracted {} to {}", entry.getName(), file.getAbsolutePath());

            final String root = (entry.getName().split("/"))[0];

            if (archive_base == null) {
                archive_base = root;
            } else if (!archive_base.equals(root)) {
                throw new IOException("Package has more than one base directory.  Archive base:" + archive_base +
                        ", root: " + root);
            }
        }

        return archive_base;
    }

    private String extract(final File dest_dir, final File file) throws ArchiveException, IOException {
        try (InputStream is = new FileInputStream(file)) {
            return extract(dest_dir, is);
        }
    }

    // Extract entry in an archive and return relative file to extracted entry
    private File extract(final File dest_dir, final ArchiveEntry entry, final ArchiveInputStream ais)
            throws IOException {
        final String path = FilenameUtils.separatorsToSystem(entry.getName());

        final File file = new File(dest_dir, path);

        if (entry.isDirectory()) {
            file.mkdirs();
        } else {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            try (OutputStream os = new FileOutputStream(file)) {
                IOUtils.copyLarge(ais, os, 0, entry.getSize());
            } catch (final IOException e) {
                throw new IOException("Couldn't create " + file.toString() +
                        ". Please make sure you have write access for the extract directory.", e);
            }
        }

        return new File(path);
    }

    /**
     * Open a package
     *
     * @param staging_dir Staging directory.
     * @param file Package fine
     * @return File for the extracted package location.
     * @throws IOException if there is a problem expanding files into the directory.
     */
    public File openPackage(final File staging_dir, final File file) throws IOException {
        try {
            final String archive_base = extract(staging_dir, file);
            return new File(staging_dir, archive_base);

        } catch (final ArchiveException e) {
            throw new IOException(e);
        }
    }

    /**
     * Open a package
     *
     * @param staging_dir Staging directory.
     * @param stream package stream.
     * @return File for the extracted package location.
     * @throws IOException if there is a problem expanding files into the directory.
     */
    public File openPackage(final File staging_dir, final InputStream stream) throws IOException {
        try {
            return new File(staging_dir, extract(staging_dir, stream));
        } catch (final ArchiveException e) {
            throw new IOException(e);
        }
    }

    private static InputStream buffered(final InputStream in) {
        if (!in.markSupported()) {
            return new BufferedInputStream(in);
        }
        return in;
    }

    private static InputStream decompress(final InputStream in) {
        try {
            return new CompressorStreamFactory().createCompressorInputStream(buffered(in));
        } catch (final CompressorException e) {
            return in;
        }
    }

    private static ArchiveInputStream archiveStream(final InputStream in) throws ArchiveException {
        final ArchiveStreamFactory af = new ArchiveStreamFactory();
        try {
            return af.createArchiveInputStream(buffered(in));
        } catch (final ArchiveException e) {
            return af.createArchiveInputStream(decompress(buffered(in)));
        }
    }
}
