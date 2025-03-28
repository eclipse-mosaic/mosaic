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

package org.eclipse.mosaic.fed.output.generator.file;

import org.eclipse.mosaic.fed.output.ambassador.AbstractOutputGenerator;
import org.eclipse.mosaic.fed.output.ambassador.ConfigHelper;
import org.eclipse.mosaic.fed.output.ambassador.OutputGeneratorLoader;
import org.eclipse.mosaic.fed.output.generator.file.format.InteractionFormatter;
import org.eclipse.mosaic.fed.output.generator.file.write.Write;
import org.eclipse.mosaic.fed.output.generator.file.write.WriteByFile;
import org.eclipse.mosaic.fed.output.generator.file.write.WriteByFileCompress;
import org.eclipse.mosaic.fed.output.generator.file.write.WriteByLog;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileOutputLoader extends OutputGeneratorLoader {

    private static final Logger log = LoggerFactory.getLogger(FileOutputLoader.class);
    private static final char DEFAULT_SEPARATOR = ',';
    private static final char DEFAULT_DECIMAL_SEPARATOR = '.';
    /* Configuration properties */
    private static final String FILE_NAME = "filename";
    private static final String DIR = "directory";
    private static final String SEPARATOR = "separator";
    private static final String DECIMAL_SEPARATOR = "decimalSeparator";
    private static final String WRITE = "write";
    private static final String APPEND = "append";

    /* Configuration values */
    private static final String WRITE_BY_FILE = "file";
    private static final String WRITE_BY_FILE_COMPRESS = "file+compress";
    private static final String WRITE_BY_LOG = "log";
    private static final String WRITE_BY_DEFAULT = WRITE_BY_LOG;
    private static final boolean APPEND_DEFAULT = true;
    private static final String DIR_DEFAULT = ".";

    private Write writer;
    private InteractionFormatter interactionFormatter;

    @Override
    public Collection<String> getInteractionTypes() {
        return this.interactionFormatter.getInteractionTypes();
    }

    public InteractionFormatter getInteractionFormatter() {
        return this.interactionFormatter;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        try {
            this.writer = this.getWrite(config);
            this.interactionFormatter = this.createInteractionFormatter(config);
        } catch (Exception e) {
            log.error("Exception", e);
            throw new RuntimeException("Caused by OutputGenerator " + getId(), e);
        }
    }

    private InteractionFormatter createInteractionFormatter(HierarchicalConfiguration<ImmutableNode> sub)
            throws SecurityException, NoSuchMethodException, ClassNotFoundException, IllegalArgumentException {
        String separatorInput = sub.getString(SEPARATOR);
        char separator = DEFAULT_SEPARATOR;
        if (separatorInput == null || separatorInput.length() != 1) {
            log.warn("separator is required to be one character, defaulting to '{}' as entry separator.", DEFAULT_SEPARATOR);
        } else {
            separator = separatorInput.charAt(0);
        }
        String decimalSeparatorInput = sub.getString(DECIMAL_SEPARATOR);
        char decimalSeparator = DEFAULT_DECIMAL_SEPARATOR;
        if (decimalSeparatorInput == null || decimalSeparatorInput.length() != 1) {
            log.warn("decimalSeparator is required to be one character, defaulting to '{}' as decimal separator.", DEFAULT_DECIMAL_SEPARATOR);
        } else {
            decimalSeparator = decimalSeparatorInput.charAt(0);
        }

        Map<String, List<List<String>>> interactionDefs = new HashMap<>();

        List<HierarchicalConfiguration<ImmutableNode>> interactionList = sub.configurationsAt("subscriptions.subscription");
        for (HierarchicalConfiguration<ImmutableNode> interaction : interactionList) {
            if (!ConfigHelper.isEnabled(interaction)) {
                continue;
            }

            String interactionId = ConfigHelper.getId(interaction);
            List<String> entries = interaction.getList("entries.entry").stream().map(String::valueOf).toList();

            interactionDefs
                    .computeIfAbsent(interactionId, (k) -> new ArrayList<>())
                    .add(entries);
        }

        return new InteractionFormatter(separator, decimalSeparator, interactionDefs);
    }

    /**
     * get write according to dir, file name and append flag. The root directory
     * is the log-directory defined in logback.xml
     *
     * @param sub sub-configuration
     * @return A Write instance for file visualizer
     */
    private Write getWrite(HierarchicalConfiguration<ImmutableNode> sub) throws IOException {
        Write ret;
        String fileName = sub.getString(FILE_NAME);
        String dir = loggerDirectory() + File.separator
                + sub.getString(DIR, DIR_DEFAULT);
        String write = sub.getString(WRITE, WRITE_BY_DEFAULT);
        boolean append = sub.getBoolean(APPEND, APPEND_DEFAULT);

        File d = new File(dir);
        if (!d.exists() && !d.mkdirs()) {
            log.warn("Could not create directory in {}", dir);
        }

        File outputFile = new File(dir + File.separator + fileName);

        ret = switch (write) {
            case WRITE_BY_LOG -> new WriteByLog(outputFile, append);
            case WRITE_BY_FILE -> new WriteByFile(outputFile, append);
            case WRITE_BY_FILE_COMPRESS -> new WriteByFileCompress(outputFile, append);
            default -> throw new IllegalArgumentException("No such write method '" + write + "'");
        };
        return ret;
    }

    @Override
    public AbstractOutputGenerator createOutputGenerator() {
        return new FileOutput(this.writer, this.getInteractionFormatter());
    }
}
