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

package org.eclipse.mosaic.fed.sumo.util;

import org.eclipse.mosaic.fed.sumo.config.CSumo;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.lib.util.ColorUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * This class creates a new SUMO additional file containing vehicle types added from RTI e.g. via Mapping and links
 * to that file in the sumo configuration. Additionally it will merge vehicle types from mapping with
 * additional parameters specified in {@link CSumo#additionalVehicleTypeParameters}.
 */
public class SumoVehicleTypesWriter {

    private final static Logger log = LoggerFactory.getLogger(SumoVehicleTypesWriter.class);

    final static String MOSAIC_TYPES_FILE_NAME = "mosaic_types.add.xml";
    /**
     * Document used to write vehicle types (prototypes) from Mapping to.
     * Note: parameters from {@link org.eclipse.mosaic.fed.sumo.config.CSumo#additionalVehicleTypeParameters}
     * will also be written into this file, overwriting values from Mapping.
     */
    private final Document vehicleTypesDocument;

    /**
     * {@link File}-object linking to the new vehicle type additional file.
     */
    private final File vehicleTypeAdditionalFile;

    private final double timeGapOffset;

    /**
     * Stores additional vehicle type parameters specified in {@link org.eclipse.mosaic.fed.sumo.config.CSumo}.
     */
    private final Map<String, Map<String, String>> additionalVehicleTypeParameters;

    /**
     * Constructor for {@link SumoVehicleTypesWriter}.
     *
     * @param sumoConfigurationDirectory this is the {@link File}-object linking to the directory containing
     * @param sumoConfiguration          the sumo configuration read from sumo_config.json
     */
    public SumoVehicleTypesWriter(File sumoConfigurationDirectory,
                                  CSumo sumoConfiguration) {
        vehicleTypeAdditionalFile = new File(sumoConfigurationDirectory, MOSAIC_TYPES_FILE_NAME);
        File sumoConfigurationFile = new File(sumoConfigurationDirectory, sumoConfiguration.sumoConfigurationFile);

        this.additionalVehicleTypeParameters = sumoConfiguration.additionalVehicleTypeParameters;
        this.timeGapOffset = sumoConfiguration.timeGapOffset;

        addVehicleTypesFileToSumoConfig(sumoConfigurationFile);
        vehicleTypesDocument = initializeDocument();
    }

    /**
     * Adds the new additional file containing the vehicle type to the sumo configuration.
     *
     * @param sumoConfigurationFile the sumo configuration
     */
    private void addVehicleTypesFileToSumoConfig(File sumoConfigurationFile) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(sumoConfigurationFile);

            NodeList additionalFilesNodes = doc.getElementsByTagName("additional-files");
            Element additionalFilesNodeElement;
            String newAdditionalFilesValue;
            if (additionalFilesNodes.getLength() == 0) { // if there is no "additional-files" element we have to create it and add
                Node newAdditionalFilesNode = doc.createElement("additional-files");

                Element inputNode = (Element) doc.getElementsByTagName("input").item(0);
                inputNode.appendChild(newAdditionalFilesNode);
                additionalFilesNodeElement = (Element) additionalFilesNodes.item(0);
                newAdditionalFilesValue = MOSAIC_TYPES_FILE_NAME;
            } else { // else prepend document
                additionalFilesNodeElement = (Element) additionalFilesNodes.item(0);
                String previousAdditionalFiles = "," + additionalFilesNodeElement.getAttribute("value");
                // prepending because vTypes have to be known before vehicle definitions
                newAdditionalFilesValue = MOSAIC_TYPES_FILE_NAME + previousAdditionalFiles;
            }
            additionalFilesNodeElement.setAttribute("value", newAdditionalFilesValue);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Result output = new StreamResult(sumoConfigurationFile);
            Source input = new DOMSource(doc);
            transformer.transform(input, output);
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            log.error("Couldn't add new vehicle Types to SUMO configuration.", e);
        }
    }

    /**
     * Initializes the document and writes the routes-tag into it.
     */
    private Document initializeDocument() {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            // set meta tags
            document.setXmlVersion("1.0");
            // initialize parent node
            Node parent = document.createElement("routes");
            document.appendChild(parent);
            return document;
        } catch (ParserConfigurationException e) {
            log.warn("Couldn't instantiate DocumentBuilder, this will result in no additional-file being written");
        }
        return null;
    }

    /**
     * Adds the given vehicle types including the already existing
     * {@link #vehicleTypesDocument}.
     */
    @SuppressWarnings("UnusedReturnValue")
    public SumoVehicleTypesWriter addVehicleTypes(Map<String, VehicleType> additionalVehicleTypes) {
        // adding new types with additional parameters from sumo config
        Map<String, Map<String, String>> newVehicleTypes = generateAttributesMap(additionalVehicleTypes, timeGapOffset);

        // applies the additional para
        applyParametersFromSumoConfiguration(newVehicleTypes);

        // write vehicle types to new route file containing vehicle types
        writeVehicleTypes(newVehicleTypes);

        return this;
    }

    private void applyParametersFromSumoConfiguration(Map<String, Map<String, String>> newVehicleTypes) {
        for (Entry<String, Map<String, String>> sumoParameterEntry : additionalVehicleTypeParameters.entrySet()) {
            String currentVehicleType = sumoParameterEntry.getKey();
            if (!newVehicleTypes.containsKey(currentVehicleType)) {
                continue; // if type defined in sumo config wasn't defined in mapping ignore the type
            }
            Set<Entry<String, String>> additionalParameters = sumoParameterEntry.getValue().entrySet();
            for (Entry<String, String> parameter : additionalParameters) {
                String parameterName = parameter.getKey();
                String parameterValue = parameter.getValue();
                newVehicleTypes.get(currentVehicleType).put(parameterName, parameterValue);
            }
        }
    }

    private void writeVehicleTypes(Map<String, Map<String, String>> newVehicleTypes) {
        Node routesNode = vehicleTypesDocument.getFirstChild();
        if (!routesNode.getNodeName().equals("routes")) {
            log.warn("Couldn't write vTypes");
            return;
        }
        for (Entry<String, Map<String, String>> attributesEntry : newVehicleTypes.entrySet()) {
            Element currentVehicleType = vehicleTypesDocument.createElement("vType");
            currentVehicleType.setAttribute("id", attributesEntry.getKey());
            for (Entry<String, String> attributeEntry : attributesEntry.getValue().entrySet()) {
                String attributeName = attributeEntry.getKey();
                String attributeValue = attributeEntry.getValue();

                if (!attributeName.contains(".")) {
                    currentVehicleType.setAttribute(attributeName, attributeValue);
                } else {
                    Element paramElem =  vehicleTypesDocument.createElement("param");
                    paramElem.setAttribute("key", attributeName);
                    paramElem.setAttribute("value", attributeValue);
                    currentVehicleType.appendChild(paramElem);
                }

            }
            routesNode.appendChild(currentVehicleType);
        }
    }

    /**
     * Generates a Map with the attributes of the vehicle.
     *
     * @param actualTypes Represents the vehicle types.
     * @return Attributes of a vehicle type.
     */
    private Map<String, Map<String, String>> generateAttributesMap(Map<String, VehicleType> actualTypes, double timeGapOffset) {
        Map<String, Map<String, String>> types = new HashMap<>();

        Map<String, String> attributes;
        for (Entry<String, VehicleType> vehicleTypesEntry : actualTypes.entrySet()) {
            attributes = types.computeIfAbsent(vehicleTypesEntry.getKey(), k -> new TreeMap<>());

            attributes.put("vClass", SumoVehicleClassMapping.toSumo(vehicleTypesEntry.getValue().getVehicleClass()));
            attributes.put("accel", formatDoubleAttribute(vehicleTypesEntry.getValue().getAccel()));
            attributes.put("decel", formatDoubleAttribute(vehicleTypesEntry.getValue().getDecel()));
            attributes.put("emergencyDecel", formatDoubleAttribute(vehicleTypesEntry.getValue().getEmergencyDecel()));
            attributes.put("length", formatDoubleAttribute(vehicleTypesEntry.getValue().getLength()));
            attributes.put("maxSpeed", formatDoubleAttribute(vehicleTypesEntry.getValue().getMaxSpeed()));
            attributes.put("minGap", formatDoubleAttribute(vehicleTypesEntry.getValue().getMinGap()));
            attributes.put("sigma", formatDoubleAttribute(vehicleTypesEntry.getValue().getSigma()));
            attributes.put("tau", formatDoubleAttribute(vehicleTypesEntry.getValue().getTau() + timeGapOffset));
            attributes.put("speedFactor", formatDoubleAttribute(vehicleTypesEntry.getValue().getSpeedFactor()));

            // We deviate the speedFactor on our own. Therefore, we must set the speedDev in SUMO to 0.0 in order
            // to not alter the already adjusted speedFactor again.
            attributes.put("speedDev", "0.0");

            Color color = ColorUtils.toColor(vehicleTypesEntry.getValue().getColor(), null);
            if (color != null) {
                attributes.putIfAbsent(
                        "color",
                        String.format(Locale.ENGLISH, "%d,%d,%d", color.getRed(), color.getGreen(), color.getBlue())
                );
            }
            attributes.putIfAbsent(
                    "carFollowModel",
                    vehicleTypesEntry.getValue().getVehicleClass() == VehicleClass.AutomatedVehicle ? "ACC" : "Krauss"
            );
            if (vehicleTypesEntry.getValue().getVehicleClass() == VehicleClass.ElectricVehicle) {
                attributes.putIfAbsent("emissionClass", "Energy/unknown");
            }
        }
        return types;
    }

    private String formatDoubleAttribute(double attribute) {
        return String.format(Locale.ENGLISH, "%.2f", attribute);
    }

    /**
     * Stores the document to the given target file.
     */
    public void store() {
        writeXmlFile(vehicleTypeAdditionalFile, vehicleTypesDocument);
    }

    /**
     * Merges the document into the existing file.
     *
     * @param file     file to write to
     * @param document {@link Document} object, containing xml-description
     */
    private void writeXmlFile(File file, Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.toString());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);
            log.info("{} file successfully written.", file.toString());
        } catch (TransformerException e) {
            log.debug("Couldn't write additional-file.");
        }
    }
}
