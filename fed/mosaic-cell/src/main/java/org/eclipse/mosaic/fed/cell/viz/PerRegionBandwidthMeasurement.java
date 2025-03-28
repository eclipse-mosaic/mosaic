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

import static java.lang.Math.max;
import static org.eclipse.mosaic.fed.cell.config.model.CNetworkProperties.GLOBAL_NETWORK_ID;

import org.eclipse.mosaic.fed.cell.config.model.CMobileNetworkProperties;
import org.eclipse.mosaic.fed.cell.config.model.TransmissionMode;
import org.eclipse.mosaic.fed.cell.data.ConfigurationData;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.apache.commons.io.Charsets;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Measures the bandwidth acquired by vehicles within each simulation time step.
 */
public class PerRegionBandwidthMeasurement implements StreamListener {

    private final static Logger log = LoggerFactory.getLogger(PerRegionBandwidthMeasurement.class);

    final static String WILDCARD_ALL = "*";

    private final static char SEPARATOR = ',';

    private final File parentDir;
    /**
     * The region id of the region that is the initiator of the transmission.
     */
    private final String from;

    /**
     * The region id of the region that is the consumer of the transmission.
     */
    private final String to;

    /**
     * The application class.
     */
    private final String applicationClass;

    /**
     * Map of a region to its column index.
     */
    private final BiMap<String, Integer> indexMap = HashBiMap.create();

    /**
     * List of the transmitted data. Each Array in the list represents one time step.
     */
    @VisibleForTesting
    final TransmittedData transmittedData = new TransmittedData();

    /**
     * The number of data rows written to the CSV file so far.
     */
    private int csvSize;

    /**
     * The name of the CSV file.
     */
    private String csvName;

    /**
     * The mode of transmission which is measured, either {@link TransmissionMode#UplinkUnicast} for bandwidth
     * produced by sending messages, or {@link TransmissionMode#DownlinkUnicast} for bandwidth produced by receivingTime messages.
     */
    private final TransmissionMode mode;

    /**
     * Defines the starting time of the bandwidth measurement in seconds.
     * Every message processed before the starting time is not considered
     * in the bandwidth measurement.
     */
    private final static long STARTING_TIME = 0;
    private final static long STARTING_TIME_NANO = STARTING_TIME * TIME.SECOND;

    /**
     * Defines the end time of the bandwidth measurement in seconds.
     * Every message processed after the starting time is not considered in
     * the bandwidth measurement.
     */
    private final static long END_TIME_NANO = Long.MAX_VALUE;

    /**
     * The interval which is used for aggregating consumed bandwidth.
     * (default: 1 second of simulation time)
     */
    private final long interval;

    private final static int EXPORT_STEP_SIZE = 600;

    private long nextExport;

    /**
     * Output stream writer for the csv file.
     */
    private OutputStreamWriter csvWriter;

    /**
     * Constructs a new PerRegionBandwidthMeasurement.
     *
     * @param from    The region from which the measured messages are sent.
     * @param to      The region to which the measured messages are sent.
     * @param mode    The transmission mode of the measured communication.
     * @param regions A list of possible regions that could be involved in the communication.
     */
    public PerRegionBandwidthMeasurement(File parentDir, String from, String to, TransmissionMode mode,
                                         String applicationClass, List<CMobileNetworkProperties> regions) {
        this.parentDir = parentDir;
        this.from = from;
        this.to = to;
        this.applicationClass = applicationClass;

        int i = 0;
        for (CMobileNetworkProperties region : regions) {
            indexMap.put(region.id, i++);
        }

        indexMap.put(GLOBAL_NETWORK_ID, i);

        this.interval = ConfigurationData.INSTANCE.getCellConfig().bandwidthMeasurementInterval * TIME.SECOND;
        this.nextExport = 2 * EXPORT_STEP_SIZE * interval;

        this.mode = mode;

        this.csvName = String.format("%s#%s#%s",
                (WILDCARD_ALL.equals(from)) ? "ALL" : from,
                (WILDCARD_ALL.equals(to)) ? "ALL" : to,
                (WILDCARD_ALL.equals(applicationClass)) ? "ALL" : applicationClass);

        if (WILDCARD_ALL.equals(from) && WILDCARD_ALL.equals(to) && WILDCARD_ALL.equals(applicationClass)) {
            csvName += "#" + (mode.isUplink() ? "Up" : "Dn");
        }

        this.transmittedData.init(indexMap.size());

        initCsv();
    }

    /**
     * Initializes the CSV file for bandwidth measurements.
     */
    private void initCsv() {
        try {
            final OutputStream out;
            if (ConfigurationData.INSTANCE.getCellConfig().bandwidthMeasurementCompression) {
                out = new GZIPOutputStream(new FileOutputStream(new File(parentDir, csvName + ".csv.gz")));
            } else {
                out = new FileOutputStream(new File(parentDir, csvName + ".csv"));
            }
            csvWriter = new OutputStreamWriter(out, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        StringBuilder b = new StringBuilder();
        b.append("time");
        for (int i = 0; i < indexMap.size(); i++) {
            b.append(SEPARATOR).append(indexMap.inverse().get(i));
        }
        writeToCsv(b.toString());
        flushCsv();
    }

    //The following methods are used to update the bandwidth measurement data during the simulation.

    /**
     * Updates the transmittedData value when a message is sent.
     *
     * @param sender     The information about the sender.
     * @param receiver   The information about the receiver.
     * @param properties The general information about the message transmission.
     */
    @Override
    public void messageSent(StreamParticipant sender, StreamParticipant receiver, StreamProperties properties) {
        if (senderMatches(sender) && receiverMatches(receiver) && applicationClassMatches(properties)) {

            long sendingTime = sender.getMessageTime();
            long receivingTime = receiver.getMessageTime();

            if (sendingTime > STARTING_TIME_NANO && sendingTime < END_TIME_NANO
                    && receivingTime > STARTING_TIME_NANO && receivingTime < END_TIME_NANO) {

                /* Generate array which contains the bandwidth for each interval the message is sent in. */
                long[] bpsPerInterval = getBandwidthPerInterval(sendingTime, receivingTime, properties.getBandwidth());

                /* determine point of time (the row index based on time and INTERVAL) at which the transmission started
                   this value is subtracted by the starting point of the bandwidth measurement
                   to avoid measurements before the starting point. */
                int startIntervalIndex = (int) ((sendingTime - STARTING_TIME_NANO - sendingTime % this.interval) / this.interval);

                // add transmitted bits to either sender or receiver region, according to transmission mode
                final int regionIndex;
                if (mode == TransmissionMode.UplinkUnicast) {
                    regionIndex = ObjectUtils.defaultIfNull(indexMap.get(sender.getRegion()), -1);
                } else {
                    regionIndex = ObjectUtils.defaultIfNull(indexMap.get(receiver.getRegion()), -1);
                }

                if (regionIndex >= 0) {
                    for (int i = startIntervalIndex; i < startIntervalIndex + bpsPerInterval.length; i++) {
                        Row transmittedData = getRow(i);
                        //add to transmitted data in region
                        transmittedData.set(regionIndex, bpsPerInterval[i - startIntervalIndex] + transmittedData.get(regionIndex));
                    }
                }
            }

            checkForExport(receivingTime);
        }
    }

    /**
     * Matches the sender.
     *
     * @param sender sends measurement.
     * @return True if sender matched.
     */
    private boolean senderMatches(StreamParticipant sender) {
        return WILDCARD_ALL.equals(from) || sender.getRegion().equals(from);
    }

    /**
     * Matches the receiver.
     *
     * @param receiver receives measurements.
     * @return True if receiver matched.
     */
    private boolean receiverMatches(StreamParticipant receiver) {
        return WILDCARD_ALL.equals(to) || receiver.getRegion().equals(to);
    }

    /**
     * Matches the application.
     *
     * @param properties Properties of the stream.
     * @return True if the application class matched.
     */
    private boolean applicationClassMatches(StreamProperties properties) {
        return WILDCARD_ALL.equals(applicationClass) || properties.getApplicationClass().equals(applicationClass);
    }

    /**
     * Writes everything to export file (CSV file).
     */
    @Override
    public void finish() {
        updateCsv(true);
        try {
            csvWriter.close();
        } catch (IOException e) {
            log.error("Could not close CSVWriter", e);
        }
    }

    /**
     * @return an array containing the bandwidth send in each time interval between sendingTime and receivingTime
     */
    private long[] getBandwidthPerInterval(long sendingTime, long receivingTime, long bandwidth) {
        final long[] bitsPerInterval;
        long sendingInterval = sendingTime / this.interval;

        //integer division where we always want to round up
        long receivingInterval = (receivingTime + this.interval - 1) / this.interval;

        long numberOfIntervals = max(1, receivingInterval - sendingInterval);
        bitsPerInterval = new long[(int) numberOfIntervals];
        Arrays.fill(bitsPerInterval, bandwidth);
        return bitsPerInterval;
    }

    /**
     * Gets the row with a specific index.
     *
     * @param rowIndex Index of the row.
     * @return Row element.
     */
    private Row getRow(int rowIndex) {
        return transmittedData.get(rowIndex);
    }

    /**
     * @param time Time to check for export.
     */
    private void checkForExport(long time) {
        if (time > nextExport) {
            updateCsv(false);
            nextExport += EXPORT_STEP_SIZE * interval;
        }
    }

    /**
     * Updates the CSV file with bandwidth measurements. The list of transmitted data is written
     * out, except the last EXPORT_STEP_SIZE items.
     *
     * @param everything if set to <code>true</code>, the complete list of transmitted data is written down
     */
    private void updateCsv(boolean everything) {
        final int exportSize;
        if (everything) {
            exportSize = transmittedData.size() - csvSize;
        } else {
            exportSize = (((transmittedData.size() - csvSize) / EXPORT_STEP_SIZE) - 1) * EXPORT_STEP_SIZE;
        }

        int endRowIndex = max(csvSize, csvSize + exportSize);

        StringBuffer b;
        for (int rowIndex = csvSize; rowIndex < endRowIndex; rowIndex++) {
            b = new StringBuffer(Long.toUnsignedString((csvSize * interval) / TIME.SECOND));

            /* Previous version:
             *      for (Long transmitted: transmittedDataList.get(rowIndex)) {
             *          b.append(SEPARATOR).append(ObjectUtils.defaultIfNull(transmitted, 0).toString());
             *      }
             * was quite slow; especially due to inefficient iteration over the array and
             * frequent conversions of zeros to string. */
            long transmitted;
            Row row = transmittedData.get(rowIndex);
            for (int i = 0; i < row.size(); i++) {
                transmitted = row.get(i);
                if (transmitted == 0L) {
                    b.append(SEPARATOR).append("0");
                } else {
                    b.append(SEPARATOR).append(Long.toUnsignedString(transmitted));
                }
            }
            transmittedData.clearRow(rowIndex);

            writeToCsv(b.toString());
            csvSize++;
        }
        flushCsv();
    }

    /**
     * Writes measurements to the csv file.
     *
     * @param s String to write to csv file.
     */
    private void writeToCsv(String s) {
        try {
            csvWriter.write(s);
            csvWriter.write(SystemUtils.LINE_SEPARATOR);
        } catch (IOException e) {
            log.error("Could not write line", e);
        }
    }

    private void flushCsv() {
        try {
            csvWriter.flush();
        } catch (IOException e) {
            log.error("Could not write line", e);
        }
    }

    /**
     * Wrapper around the actual list of transmitted data.
     * In order to consume less memory, rows which have been written out
     * to CSV are replaced with null references.
     */
    static class TransmittedData {

        private final ArrayList<Row> transmittedDataList = new ArrayList<>();
        private int columnSize;

        void init(int columnSize) {
            this.columnSize = columnSize;
        }

        int size() {
            return transmittedDataList.size();
        }

        Row get(int rowIndex) {
            if (size() <= rowIndex) {
                for (int i = size(); i <= rowIndex; i++) {
                    transmittedDataList.add(null);
                }
            }
            Row row = transmittedDataList.get(rowIndex);
            if (row == null) {
                row = new Row(columnSize);
                transmittedDataList.set(rowIndex, row);
            }
            return row;
        }

        void clearRow(int rowIndex) {
            transmittedDataList.set(rowIndex, null);
        }
    }

    /**
     * Wrapper of long array to reduce memory consumption, as the array
     * which holds the information is only required if needed (e.g. rows
     * without any measurements should not consume memory)
     */
    static class Row {

        private Long[] content = null;
        private final int size;

        private Row(int size) {
            this.size = size;
        }

        long get(int column) {
            if (content == null) {
                return 0;
            }
            return ObjectUtils.defaultIfNull(content[column], 0L);
        }

        void set(int column, long value) {
            if (content == null) {
                content = new Long[size];
            }
            content[column] = value;
        }

        public int size() {
            return size;
        }
    }
}
