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

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.commons.configuration2.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;

public class XmlUtils {

    private final static int DEFAULT_XML_INDENTATION = 4;

    private XmlUtils() {
        // static methods only
    }

    public static XMLConfiguration createEmptyConfigurationWithIndentation(final int indentation) {
        return new XMLConfiguration() {

            @Override
            protected Transformer createTransformer() throws ConfigurationException {
                Transformer transformer = super.createTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(indentation));
                return transformer;
            }
        };
    }

    public static XMLConfiguration readXmlFromFile(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException(file.getAbsolutePath() + " cannot be found");
        }
        final XMLConfiguration configuration = createEmptyConfigurationWithIndentation(DEFAULT_XML_INDENTATION);
        try {
            new FileHandler(configuration).load(file);
        } catch (ConfigurationException e) {
            throw new IOException("Invalid XML syntax", e);
        }
        return configuration;
    }

    public static XMLConfiguration readXmlFromStream(InputStream inputStream) throws IOException {
        final XMLConfiguration configuration = createEmptyConfigurationWithIndentation(DEFAULT_XML_INDENTATION);
        try {
            new FileHandler(configuration).load(inputStream);
        } catch (ConfigurationException e) {
            throw new IOException("Invalid XML syntax", e);
        }
        return configuration;
    }

    public static void writeXmlToFile(XMLConfiguration configuration, File file) throws IOException {
        try {
            new FileHandler(configuration).save(file);
        } catch (ConfigurationException e) {
            throw new IOException("Invalid XML configuration", e);
        }
    }

    public static Stream<String> convertTagToStream(HierarchicalConfiguration<ImmutableNode> node, String tag) {
        return node.getList(tag).stream().map(ObjectUtils::identityToString);
    }

    public static String convertTagToString(HierarchicalConfiguration<ImmutableNode> node, String tag) {
        return convertTagToStream(node, tag).collect(Collectors.joining(","));
    }

    public static String getValueFromXpath(XMLConfiguration xmlConfiguration, String xpath, String defaultValue) {
        xmlConfiguration.setExpressionEngine(new XPathExpressionEngine());
        return xmlConfiguration.getString(xpath, defaultValue);
    }

}
