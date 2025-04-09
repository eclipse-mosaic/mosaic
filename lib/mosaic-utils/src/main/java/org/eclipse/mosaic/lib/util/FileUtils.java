/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.lib.util;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class FileUtils {

    private static final int MAX_DEPTH = 256;

    private FileUtils() {
        // static method only
    }

    public static Collection<File> searchForFiles(File rootFile, Predicate<File> acceptPredicate) {
        return searchForFiles(rootFile, acceptPredicate, MAX_DEPTH);
    }

    public static Collection<File> searchForFiles(File rootFile, Predicate<File> acceptPredicate, int maxDepth) {
        return searchForFiles(Collections.singleton(rootFile), acceptPredicate, maxDepth);
    }

    public static Collection<File> searchForFiles(Collection<File> rootFiles, Predicate<File> acceptPredicate) {
        return searchForFiles(rootFiles, acceptPredicate, MAX_DEPTH);
    }

    public static Collection<File> searchForFiles(Collection<File> rootFiles, Predicate<File> acceptPredicate, int maxDepth) {
        Set<File> searchSet = new HashSet<>(rootFiles);

        final Set<File> matchingSet = new HashSet<>();
        while (maxDepth-- > 0) {
            if (!searchSet.isEmpty()) {
                Set<File> newDirectorySet = new HashSet<>();
                for (File f : searchSet) {
                    if (f.canRead() && acceptPredicate.test(f)) {
                        if (f.isDirectory()) {
                            File[] listFiles = f.listFiles();
                            if (listFiles != null) {
                                newDirectorySet.addAll(Arrays.asList(listFiles));
                            }
                        } else if (f.isFile()) {
                            matchingSet.add(f);
                        }
                    }
                }
                searchSet = newDirectorySet;
            } else {
                return matchingSet;
            }
        }
        return matchingSet;
    }

    public static Collection<File> searchForFilesOfType(File rootFile, String fileEnding) {
        return searchForFilesOfType(Collections.singleton(rootFile), fileEnding);
    }

    public static Collection<File> searchForFilesOfType(Collection<File> rootFiles, String fileEnding) {
        final String fileEndingForSearch = fileEnding.startsWith(".")
                ? fileEnding.toLowerCase()
                : "." + fileEnding.toLowerCase();
        return searchForFiles(rootFiles, f ->
                f.canRead() && (f.isDirectory() || (f.isFile() && f.getName().toLowerCase().endsWith(fileEndingForSearch)))
        );
    }

    public static Collection<File> searchForFilesWithName(File rootFile, String fileName) {
        return searchForFilesWithName(Collections.singleton(rootFile), fileName);
    }

    public static Collection<File> searchForFilesWithName(Collection<File> rootFiles, String fileName) {
        return searchForFiles(rootFiles, f ->
                f.canRead() && (f.isDirectory() || (f.isFile() && f.getName().toLowerCase().equals(fileName.toLowerCase())))
        );
    }

    /**
     * Creates a new file in the given path and name. If the file already exists, a number suffix
     * is added to the actual file name.
     *
     * @param path        the path to the file to create.
     * @param ignoreExist if {@code true}, no check is done and the file is returned as requested.
     * @return the actual file object with the name having a suffix if already exists
     */
    public static File getIncrementFile(String path, boolean ignoreExist) throws IOException {
        return getIncrementFile(new File(path), ignoreExist);
    }

    /**
     * Creates a new file in the given path and name. If the file already exists, a number suffix
     * is added to the actual file name, by respecting the given file extension.
     *
     * @param path        the path to the file to create.
     * @param extension   provides the file extension (e.g., ".rou.xml").
     * @param ignoreExist if {@code true}, no check is done and the file is returned as requested.
     * @return the actual file object with the name having a suffix if already exists
     */
    public static File getIncrementFile(String path, String extension, boolean ignoreExist) throws IOException {
        return getIncrementFile(new File(path), extension, ignoreExist);
    }

    /**
     * Creates a new file in the given path and name. If the file already exists, a number suffix
     * is added to the actual file name.
     *
     * @param file        the file to create
     * @param ignoreExist if {@code true}, no check is done and the file is returned as requested.
     * @return the actual file object with the name having a suffix if already exists
     */
    public static File getIncrementFile(File file, boolean ignoreExist) throws IOException {
        if (file.exists() && !ignoreExist) {
            String filename = file.getName();
            String extension = "";
            int pos = filename.lastIndexOf('.');
            if (pos > 0) {
                extension = filename.substring(pos);
            }
            return getIncrementFile(file, extension, false);
        }
        return file;
    }

    /**
     * Creates a new file in the given path and name. If the file already exists, a number suffix
     * is added to the actual file name, by respecting the given file extension.
     *
     * @param file        the file to create
     * @param extension   provides the file extension (e.g., ".rou.xml").
     * @param ignoreExist if {@code true}, no check is done and the file is returned as requested.
     * @return the actual file object with the name having a suffix if already exists
     */
    public static File getIncrementFile(File file, String extension, boolean ignoreExist) throws IOException {
        if (file.exists() && !ignoreExist) {
            if (!file.getName().endsWith(extension)) {
                throw new IllegalArgumentException("Provided file does not end with provided extension.");
            }
            if (StringUtils.isNotEmpty(extension) && !extension.startsWith(".")) {
                extension = "." + extension;
            }

            String pathWithoutExtension = file.getCanonicalPath();
            pathWithoutExtension = pathWithoutExtension.substring(0, pathWithoutExtension.length() - extension.length());

            for (int i = 0; i < Integer.MAX_VALUE; ++i) {
                File newFile = new File(pathWithoutExtension + '-' + i + extension);
                if (!newFile.exists()) {
                    return newFile;
                }
            }
        }
        return file;
    }
}
