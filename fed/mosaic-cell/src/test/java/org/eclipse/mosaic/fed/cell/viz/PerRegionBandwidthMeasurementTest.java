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

package org.eclipse.mosaic.fed.cell.viz;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.fed.cell.config.model.CMobileNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.fed.cell.junit.CellConfigurationRule;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class PerRegionBandwidthMeasurementTest {

    private PerRegionBandwidthMeasurement measurement;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Rule
    public CellConfigurationRule cellConfigurationRule = new CellConfigurationRule().withCellConfig("configs/sample_cell.json");

    private File targetFile;

    private void setup() throws IOException {
        CMobileNetworkProperties fromRegion = new CMobileNetworkProperties();
        fromRegion.id = "fromRegion";
        CMobileNetworkProperties toRegion = new CMobileNetworkProperties();
        toRegion.id = "toRegion";

        List<CMobileNetworkProperties> regions = Lists.newArrayList(fromRegion, toRegion);

        File parentDir = tempDir.newFolder("bandwidthMeasurements");

        measurement =
                new PerRegionBandwidthMeasurement(parentDir, "fromRegion", "toRegion", TransmissionMode.DownlinkUnicast, "*", regions);
        targetFile = new File(parentDir, "fromRegion#toRegion#ALL.csv");
    }

    @After
    public void tearDown() {
        if (measurement != null) {
            measurement.finish();
        }
    }

    @Test
    public void shortTransmissionOfOnePacket() throws IOException {
        setup();

        // RUN
        sendPacketWithSize(1.2, 1.8, 300);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 3);
        assertEquals(0, bandwidthMeasurement[0]);
        assertEquals(500, bandwidthMeasurement[1]);
        assertEquals(0, bandwidthMeasurement[2]);
    }

    @Test
    public void longTransmissionOfOnePacket() throws IOException {
        setup();

        // RUN
        sendPacketWithSize(0.8, 2.4, 8000);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 4);
        assertEquals(5000, bandwidthMeasurement[0]);
        assertEquals(5000, bandwidthMeasurement[1]);
        assertEquals(5000, bandwidthMeasurement[2]);
        assertEquals(0, bandwidthMeasurement[3]);
    }

    @Test
    public void transmissionOfThreePacket() throws IOException {
        setup();

        // RUN
        sendPacketWithSize(1.2, 1.8, 300);
        sendPacketWithSize(1.8, 2.4, 600);
        sendPacketWithSize(0.6, 1.2, 300);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 4);
        assertEquals(500, bandwidthMeasurement[0]);
        assertEquals(2000, bandwidthMeasurement[1]);
        assertEquals(1000, bandwidthMeasurement[2]);
        assertEquals(0, bandwidthMeasurement[3]);
    }

    @Test
    public void shortTransmissionOfOneStream() throws IOException {
        setup();

        // RUN
        sendStreamWithBandwidth(1.2, 1.8, 300);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 3);
        assertEquals(0, bandwidthMeasurement[0]);
        assertEquals(300, bandwidthMeasurement[1]);
        assertEquals(0, bandwidthMeasurement[2]);
    }

    @Test
    public void longTransmissionOfOneStream() throws IOException {
        setup();

        // RUN
        sendStreamWithBandwidth(1.2, 3.4, 8000);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 5);
        assertEquals(0, bandwidthMeasurement[0]);
        assertEquals(8000, bandwidthMeasurement[1]);
        assertEquals(8000, bandwidthMeasurement[2]);
        assertEquals(8000, bandwidthMeasurement[3]);
        assertEquals(0, bandwidthMeasurement[4]);
    }

    @Test
    public void transmissionOfThreeStream() throws IOException {
        setup();

        // RUN
        sendStreamWithBandwidth(1.2, 1.8, 600);
        sendStreamWithBandwidth(0.8, 1.2, 1000);
        sendStreamWithBandwidth(1.6, 3.2, 800);

        // ASSERT
        long[] bandwidthMeasurement = getMeasurementTable(1, 5);
        assertEquals(1000, bandwidthMeasurement[0]);
        assertEquals(2400, bandwidthMeasurement[1]);
        assertEquals(800, bandwidthMeasurement[2]);
        assertEquals(800, bandwidthMeasurement[3]);
        assertEquals(0, bandwidthMeasurement[4]);
    }

    @Test
    public void csvExportInIntervals() throws IOException {
        setup();

        sendNMessages(90, 10);
        assertNumberOfLinesInExportedCsv(1);

        sendNMessages(250, 10);
        assertNumberOfLinesInExportedCsv(1);

        sendNMessages(596, 1);
        assertNumberOfLinesInExportedCsv(1);

        sendNMessages(1190, 10);
        assertNumberOfLinesInExportedCsv(601);

        sendNMessages(1500, 10);
        assertNumberOfLinesInExportedCsv(601);

        sendNMessages(1790, 10);
        assertNumberOfLinesInExportedCsv(1201);

        sendNMessages(3600, 1);
        assertNumberOfLinesInExportedCsv(3001);

        measurement.finish();
        measurement = null;

        assertNumberOfLinesInExportedCsv(3606);
        assertExportedCsvFile();
    }

    @Test
    public void csvExportInIntervals_withCompression() throws IOException {
        ConfigurationData.INSTANCE.getCellConfig().bandwidthMeasurementCompression = true;
        setup();

        // RUN
        sendNMessages(90, 10);
        sendNMessages(250, 10);
        sendNMessages(596, 1);
        sendNMessages(1190, 10);
        sendNMessages(1500, 10);
        sendNMessages(1790, 10);
        sendNMessages(3600, 1);
        measurement.finish();
        measurement = null;

        // ASSERT
        InputStream gzippedTargetStream =
                new GZIPInputStream(FileUtils.openInputStream(new File(targetFile.getParent(), targetFile.getName() + ".gz")));
        assertExportedCsvFile(gzippedTargetStream);
    }

    private void assertExportedCsvFile() throws IOException {
        assertExportedCsvFile(FileUtils.openInputStream(targetFile));
    }

    private void assertExportedCsvFile(InputStream inputStream) throws IOException {
        assertEquals(
                IOUtils.readLines(this.getClass().getResourceAsStream(
                        "/bandwidthMeasurements/fromRegion#toRegion#ALL.csv"),
                        StandardCharsets.UTF_8
                ),
                IOUtils.readLines(inputStream, StandardCharsets.UTF_8)
        );
    }

    private void assertNumberOfLinesInExportedCsv(int numberOfLines) throws IOException {
        assertEquals(numberOfLines, Files.readLines(targetFile, StandardCharsets.UTF_8).size());
    }

    private void sendNMessages(int t, int n) {
        for (int i = t; i < t + n; i++) {
            measurement.messageSent(new StreamListener.StreamParticipant("fromRegion", i * TIME.SECOND),
                    new StreamListener.StreamParticipant("toRegion", (i + 5) * TIME.SECOND),
                    new StreamListener.StreamProperties("*", 15000000L));
        }
    }

    private void sendPacketWithSize(double startTransmission, double endTransmission, int bits) {
        measurement.messageSent(
                new StreamListener.StreamParticipant("fromRegion", (long) (startTransmission * TIME.SECOND)),
                new StreamListener.StreamParticipant("toRegion", (long) (endTransmission * TIME.SECOND)),
                new StreamListener.StreamProperties("*", Math.round(bits / (endTransmission - startTransmission))));
    }

    private void sendStreamWithBandwidth(double startTransmission, double endTransmission, long bitsPerSecond) {
        measurement.messageSent(
                new StreamListener.StreamParticipant("fromRegion", (long) (startTransmission * TIME.SECOND)),
                new StreamListener.StreamParticipant("toRegion", (long) (endTransmission * TIME.SECOND)),
                new StreamListener.StreamProperties("*", bitsPerSecond));
    }

    private long[] getMeasurementTable(int columns, int rows) {
        long[] measurement = new long[rows];
        Arrays.fill(measurement, 0L);
        for (int i = 0; i < this.measurement.transmittedData.size(); i++) {
            measurement[i] = this.measurement.transmittedData.get(i).get(columns);
        }
        return measurement;
    }
}