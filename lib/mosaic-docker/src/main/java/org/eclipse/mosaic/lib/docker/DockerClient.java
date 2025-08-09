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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple client which is able to run docker images using the command line tools of docker.
 * Note, the command "docker" must be available in $PATH
 */
public class DockerClient {

    static long DOCKER_RUN_TIMEOUT_MILLISECONDS = 10000;

    private final DockerCommandLine docker;
    private final Vector<DockerContainer> runningContainers = new Vector<>();

    public DockerClient() {
        this(new DockerCommandLine());
    }

    DockerClient(DockerCommandLine commandLine) {
        docker = commandLine;
    }

    /**
     * Compose a docker run command by calling this method, building your command, and eventually calling {@link DockerRun#execute()}.
     *
     * @return an instance of {@link DockerRun} which can be used to compose and execute the docker run command
     */
    public DockerRun run(String image) {
        return new DockerRun(this, image);
    }

    /**
     * Close all containers which have been started by this container.
     */
    public void close() {
        for (DockerContainer container : runningContainers) {
            docker.kill(container.getName());
            docker.rm(container.getName());
        }
    }

    DockerContainer runImage(String image, String containerName, List<String> options, boolean removeBeforeRun) {
        // if no specific ports are published, publish all ports of container
        if (!options.contains("-p")) {
            options.add("-P");
        }
        // set name of container to default value if it hasn't been set
        containerName = Objects.toString(containerName, image);
        if (!options.contains("--name")) {
            options.add("--name");
            options.add(containerName);
        }
        // kill and remove container if flag is set
        if (removeBeforeRun) {
            docker.kill(containerName);
            docker.rm(containerName);
        }

        // run and detach ...
        docker.runAndDetach(image, options);

        // ..., so we can wait until it's up running
        waitUntilRunning(containerName, DOCKER_RUN_TIMEOUT_MILLISECONDS);

        // when container is up running, catch and follow output of container
        final Process p = docker.followLogs(containerName);

        // grab the dynamic port bindings for connecting to the processes inside the container
        final List<Pair<Integer, Integer>> portBindings = readPortBinding(containerName);

        final DockerContainer container = new DockerContainer(image, containerName, portBindings);
        container.appendProcess(p);
        runningContainers.add(container);

        return container;
    }

    /**
     * Wait until container is up running.
     *
     * @param timeout time in milliseconds to wait at most
     */
    private void waitUntilRunning(String name, long timeout) {
        String result = "";
        long start = System.currentTimeMillis();
        while (!result.startsWith("Up ")) {
            result = docker.status(name);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new DockerRuntimeException(e);
            }
            if (System.currentTimeMillis() - start > timeout) {
                throw new DockerRuntimeException("Timeout starting container with name " + name + ".");
            }
        }
    }

    /**
     * Kills a container which has been executed by this client.
     *
     * @param container the container which has been returned previously by {@link #run(String)}
     * @param remove    {@code true}, if this container should be removed after being killed
     */
    public void killContainer(DockerContainer container, boolean remove) {
        if (runningContainers.remove(container)) {
            docker.kill(container.getName());
            if (remove) {
                docker.rm(container.getName());
            }
        }
    }

    private final static Pattern PORT_PATTERN = Pattern.compile("^([0-9]+)\\/.*:([0-9]+)$");

    /**
     * Reads a list of port bindings of the given container.
     *
     * @param containerName the name of the container
     * @return a list of port bindings of the given container
     */
    private List<Pair<Integer, Integer>> readPortBinding(String containerName) {
        final List<Pair<Integer, Integer>> bindings = new Vector<>();
        String result = docker.port(containerName).replace("\r", "");
        for (String line : StringUtils.split(result, "\n")) {
            Matcher m = PORT_PATTERN.matcher(line);
            if (m.find() && m.groupCount() == 2) {
                bindings.add(Pair.of(Integer.valueOf(m.group(2)), Integer.valueOf(m.group(1))));
            }
        }

        return bindings;
    }
}
