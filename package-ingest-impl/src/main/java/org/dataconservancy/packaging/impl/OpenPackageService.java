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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Service for opening up a package file for ingest.
 *
 * This code is duplicated from OpenPackageServiceImpl in the Package Tool codebase.
 */
public class OpenPackageService {

    /**
     * Extract contents of an archive.
     *
     * @param dest_dir
     *            Destination to write archive content.
     * @param is
     *            Archive file.
     * @return Name of package base directory in dest_dir
     * @throws ArchiveException if there is an error creating the ArchiveInputStream
     * @throws IOException  if there is more than one package root
     */
    private String extract(File dest_dir, InputStream is) throws ArchiveException, IOException {
        // Apache commons compress requires buffered input streams

        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }

        // If file is compressed, uncompress.

        try {
            is = new CompressorStreamFactory().createCompressorInputStream(is);
        } catch (CompressorException e) {
        }

        // Extract entries from archive

        if (!is.markSupported()) {
            is = new BufferedInputStream(is);
        }

        String archive_base = null;

        ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(is);
        ArchiveEntry entry;

        while ((entry = ais.getNextEntry()) != null) {
            
            File file = extract(dest_dir, entry, ais);

            String root = get_root_file_name(file);

            if (archive_base == null) {
                archive_base = root;
            } else if (!archive_base.equals(root)) {
                throw new IOException("Package has more than one base directory.");
            }
        }

        return archive_base;
    }

    private String get_root_file_name(File file) {
        String root;

        do {
            root = file.getName();
        } while ((file = file.getParentFile()) != null);

        return root;
    }

    private String extract(File dest_dir, File file) throws ArchiveException, IOException {
        try (InputStream is = new FileInputStream(file)) {
            return extract(dest_dir, is);
        }
    }

    // Extract entry in an archive and return relative file to extracted entry
    private File extract(File dest_dir, ArchiveEntry entry, ArchiveInputStream ais) throws IOException {
        String path = FilenameUtils.separatorsToSystem(entry.getName());
        

        File file = new File(dest_dir, path);

        if (entry.isDirectory()) {
            file.mkdirs();
        } else {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            try (OutputStream os = new FileOutputStream(file)) {
                IOUtils.copyLarge(ais, os, 0, entry.getSize());
            } catch (IOException e) {
                throw new IOException("Couldn't create " + file.toString() + ". Please make sure you have write access for the extract directory.", e);
            }
        }

        return new File(path);
    }

    public File openPackage(File staging_dir, File file) throws IOException {
        try {
            String archive_base = extract(staging_dir, file);
            return new File(staging_dir, archive_base);

        } catch (ArchiveException e) {
            throw new IOException(e);
        }
    }
}
