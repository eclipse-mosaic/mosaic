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

import com.google.common.base.Charsets;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * A thread which reads lines from a provided {@link InputStream} (e.g., stdout of a process), and
 * feeds them into a {@link Consumer} to print or log the read lines.
 */
public class ProcessLoggingThread extends Thread {

    private final String processName;
    private final Consumer<String> lineConsumer;
    private final InputStream stream;
    private boolean started = false;
    private boolean closed = false;

    private final List<TeeInputStream> tees = new ArrayList<>();

    public ProcessLoggingThread(String processName, InputStream stream, Consumer<String> lineConsumer) {
        this.processName = processName;
        this.stream = stream;
        this.lineConsumer = lineConsumer;
    }

    /**
     * An {@link InputStream} cannot be read twice. Reading it simultaneously results in unexpected results and blocking.
     * Therefore, this method creates a copy of the initially provided {@link InputStream} which can be read simultaneously
     * by another thread by using a buffer which is filled by reading the initially provided stream.
     *
     * <br><br>Note: If the returned {@link InputStream} is not processed/read constantly, lines produced by the initial
     * input stream might be lost, since only a limited number of lines are buffered.
     *
     * @return an {@link InputStream} returning the output produced by the initially provided InputStream
     */
    public InputStream teeInputStream() {
        if (started) {
            throw new IllegalStateException("Tee must be created before starting process logging thread.");
        }
        final TeeInputStream teeInputStream = new TeeInputStream(this);
        tees.add(teeInputStream);
        return teeInputStream;
    }

    public void close() {
        closed = true;
    }

    @Override
    public void run() {
        if (closed) {
            throw new IllegalStateException("ProcessLoggingThread cannot be started twice.");
        }
        started = true;
        flushLog(this.stream);
    }

    /**
     * Flushes the stream to the given logback logger.
     *
     * @param stream stream to flush
     */
    @SuppressWarnings(value = "REC_CATCH_EXCEPTION",
            justification = "Read exception will always occur if something goes wrong and the process we monitor is dead.")
    private void flushLog(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            String line;
            while (!closed) {
                if ((line = reader.readLine()) != null) {
                    for (TeeInputStream teeInputStream : tees) {
                        // if the queue is full, we don't add anymore lines, to avoid blocking.
                        if (teeInputStream.lineQueue.remainingCapacity() > 0) {
                            teeInputStream.lineQueue.add(line);
                        }
                    }
                    lineConsumer.accept("Process " + processName + ": " + line);
                }
            }

        } catch (Exception ex) {
            /* Read exception will always occur, if something goes wrong and the process we monitor is dead.
             * Therefore it is a normal behavior and it is safe to ignore them.
             */
        }
    }

    /**
     * Anything the parent {@link ProcessLoggingThread} reads from the initially provided
     * {@link InputStream} is written in a queue. This class provides the possibility
     * to read and empty that queue by extending a {@link PipedInputStream}, thus providing
     * a tee input stream (basically, a copy of the initially provided {@link InputStream}).
     * A thread is used to wait until new lines are added to the queue to make them available
     * for consumers of this {@link InputStream}.
     */
    private static class TeeInputStream extends PipedInputStream {

        private final static int LINE_BUFFER_SIZE = 1000;

        private final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>(LINE_BUFFER_SIZE);

        private TeeInputStream(ProcessLoggingThread parent) {
            new Thread(() -> {
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new PipedOutputStream(this), Charsets.UTF_8))) {
                    while (!parent.closed) {
                        writer.write(lineQueue.take());
                        writer.newLine();
                        writer.flush();
                    }
                    writer.write(-1); // EOF
                    writer.flush();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    parent.tees.remove(this);
                }
            }).start();
        }
    }
}
