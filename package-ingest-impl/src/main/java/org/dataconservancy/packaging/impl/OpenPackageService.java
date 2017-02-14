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

/**
 * Service for opening up a package file for ingest.
 * <p>
 * This code is duplicated from OpenPackageServiceImpl in the Package Tool codebase.
 * </p>
 *
 * @author mpatton@jhu.edu
 */
public class OpenPackageService {

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
        // Apache commons compress requires buffered input streams

        InputStream is;
        BufferedInputStream buf = null;

        try {
            buf = new BufferedInputStream(i);
            is = new CompressorStreamFactory().createCompressorInputStream(buf);
        } catch (final CompressorException e) {
            buf.reset();
            IOUtils.copy(buf, new FileOutputStream("target/input.tar.gz"));
            throw new IOException("Could not create compressed input stream", e);
        }

        // Extract entries from archive

        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }

        String archive_base = null;

        final ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(is);
        ArchiveEntry entry;

        while ((entry = ais.getNextEntry()) != null) {

            final File file = extract(dest_dir, entry, ais);

            final String root = get_root_file_name(file);

            if (archive_base == null) {
                archive_base = root;
            } else if (!archive_base.equals(root)) {
                throw new IOException("Package has more than one base directory.");
            }
        }

        return archive_base;
    }

    private String get_root_file_name(final File file) {
        String root;
        File f = file;

        do {
            root = file.getName();
        } while ((f = f.getParentFile()) != null);

        return root;
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
}
