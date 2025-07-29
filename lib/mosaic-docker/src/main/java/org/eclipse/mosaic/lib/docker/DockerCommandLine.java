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

package org.eclipse.mosaic.lib.docker;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Binds docker command line options to methods.
 */
public class DockerCommandLine {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private long nextTimeout = 0;

    protected Process execCommand(String... cmd) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Execute docker command: $ docker {}", StringUtils.join(cmd, " "));
            }
            return Runtime.getRuntime().exec(ArrayUtils.insert(0, cmd, "docker"));
        } catch (IOException e) {
            throw new DockerRuntimeException(String.format("Error during command execution: %s", StringUtils.join(cmd, " ")), e);
        }
    }

    /**
     * Executes a command and waits for the execution to finish.
     * Return value not used since processes are assumed to execute correctly.
     *
     * @param cmd command array
     */
    private int execAndWaitForCommand(String... cmd) {
        try {
            final Process p = execCommand(cmd);
            if (nextTimeout <= 0) {
                p.waitFor();
            } else if (!p.waitFor(nextTimeout, TimeUnit.NANOSECONDS)) {
                throw new DockerRuntimeException("Docker Process timed out: " + StringUtils.join(cmd, " "));
            }
            return p.exitValue();
        } catch (InterruptedException e) {
            throw new DockerRuntimeException("Timeout while waiting for process", e);
        } finally {
            nextTimeout = 0;
        }
    }

    private String execCommandAndRead(String... cmd) {
        final Process p = execCommand(cmd);
        try {
            if (nextTimeout <= 0) {
                p.waitFor();
            } else if (!p.waitFor(nextTimeout, TimeUnit.NANOSECONDS)) {
                throw new DockerRuntimeException("Docker Process timed out: " + StringUtils.join(cmd, " "));
            }
            String result = readFromProcess(p.getInputStream());
            if (p.exitValue() == 0) {
                logger.debug("Docker command result: {}", result);
                return result;
            } else {
                String error = readFromProcess(p.getErrorStream());
                throw new DockerRuntimeException("A docker command returned an error: \n" + StringUtils.defaultIfBlank(error, result));
            }
        } catch (InterruptedException | IOException e) {
            throw new DockerRuntimeException("Could not read output from process", e);
        } finally {
            nextTimeout = 0;
        }
    }

    /**
     * Concatenates a {@link String}-array, which is used to
     * start a detached docker container with the given options.
     *
     * @param image   the docker image
     * @param options additional options for the image
     * @return the exit code of the executed process
     */
    public String runAndDetach(String image, List<String> options) {
        final String[] cmd = new String[options.size() + 3];
        int i = 0;
        cmd[i++] = "run";
        for (String option : options) {
            cmd[i++] = option;
        }
        cmd[i++] = "-d";
        cmd[i] = image;
        return execCommandAndRead(cmd);
    }

    /**
     * Concatenates a {@link String}-array, which is used to
     * start an attached docker container with the given options.
     *
     * @param image   the docker image
     * @param options additional options for the image
     * @return the process running the docker container
     */
    public Process run(String image, List<String> options) {
        final String[] cmd = new String[options.size() + 2];
        int i = 0;
        cmd[i++] = "run";
        for (String option : options) {
            cmd[i++] = option;
        }
        cmd[i] = image;
        return execCommand(cmd);
    }

    public Process attach(String container) {
        return execCommand("attach", container);
    }

    public Process followLogs(String container) {
        return execCommand("logs", "-f", container);
    }

    public void kill(String containerName) {
        int exitCode = execAndWaitForCommand("kill", containerName);
        logger.info("Kill command exited with code {}.", exitCode);
    }

    public void rm(String containerName) {
        int exitCode = execAndWaitForCommand("rm", "-f", containerName);
        logger.info("Remove command exited with code {}.", exitCode);

    }

    public String status(String containerName) {
        return withTimeout(TimeUnit.SECONDS.toNanos(10)).execCommandAndRead("ps", "-f", "name=" + containerName, "--format", "{{.Status}}");
    }

    public String port(String containerName) {
        return withTimeout(TimeUnit.SECONDS.toNanos(10)).execCommandAndRead("port", containerName);
    }

    private String readFromProcess(InputStream stream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(stream, baos);
        return baos.toString(StandardCharsets.UTF_8).stripTrailing();
    }

    private DockerCommandLine withTimeout(long nanos) {
        nextTimeout = nanos;
        return this;
    }

}
