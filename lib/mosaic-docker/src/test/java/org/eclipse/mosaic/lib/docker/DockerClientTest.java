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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Test suite for the {@link DockerClient}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DockerClientTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    public DockerCommandLine commandLine;

    private DockerClient dockerClient;

    @Before
    public void setUp() {
        dockerClient = new DockerClient(commandLine);

        when(commandLine.status(anyString())).thenReturn("", "", "Up 2 seconds");
        when(commandLine.port(anyString())).thenReturn("1337/tcp -> 0.0.0.0:7331\n");

        System.setProperty("mosaic.docker.no-detach", "false");
    }

    @Test
    public void run_noPortBinding_readPortsFromDocker() {
        //SETUP
        String imageName = "test-image";
        String containerName = "test-image-container";

        //RUN
        DockerContainer container = dockerClient.run(imageName).name(containerName).execute();

        //VERIFY
        assertNotNull(container);
        verify(commandLine).run(eq(imageName), argThat(containsInOrder("-P", "--name", "test-image-container")));
        verify(commandLine, never()).kill(anyString());
        verify(commandLine, never()).rm(anyString());
        verify(commandLine, times(3)).status(eq(containerName));

        assertEquals(1, container.getPortBindings().size());
        assertEquals(7331, (int) container.getPortBindings().get(0).getLeft());
        assertEquals(1337, (int) container.getPortBindings().get(0).getRight());
    }

    @Test
    public void run_declarePortBinding() {
        //SETUP
        String imageName = "test-image";
        String containerName = "test-image-container";

        //RUN
        DockerContainer container = dockerClient.run(imageName).portBinding(1337, 7331).name(containerName).execute();

        //VERIFY
        assertNotNull(container);
        verify(commandLine).run(eq(imageName), argThat(containsInOrder("-p", "1337:7331", "--name", "test-image-container")));
        verify(commandLine, never()).kill(anyString());
        verify(commandLine, never()).rm(anyString());
        verify(commandLine, times(3)).status(eq(containerName));
    }



    @Test
    public void run_declareVolumeBinding() throws Exception {
        //SETUP
        String imageName = "test-image";
        String containerName = "test-image-container";
        File tmpFile = tempFolder.newFile();

        //RUN
        DockerContainer container = dockerClient.run(imageName).volumeBinding(tmpFile, "/home/test").name(containerName).execute();

        //VERIFY
        assertNotNull(container);
        String tmpFilePath = tmpFile.getAbsolutePath().replace("\\", "/").replace(" ", "\\ ");
        verify(commandLine).run(
                eq(imageName),
                argThat(containsInOrder("-v", tmpFilePath + ":/home/test", "-P", "--name", "test-image-container"))
        );
        verify(commandLine, never()).kill(anyString());
        verify(commandLine, never()).rm(anyString());
        verify(commandLine, times(3)).status(eq(containerName));
    }

    @Test
    public void run_removeBeforeRun() {
        //SETUP
        String imageName = "test-image";

        //RUN
        DockerContainer container = dockerClient.run(imageName).removeBeforeRun().execute();

        //VERIFY
        assertNotNull(container);
        verify(commandLine).run(eq(imageName), argThat(containsInOrder("-P", "--name", imageName)));
        verify(commandLine).kill(eq(imageName));
        verify(commandLine).rm(eq(imageName));
    }

    @Test
    public void run_removeAfterRun() {
        //SETUP
        String imageName = "test-image";

        //RUN
        DockerContainer container = dockerClient.run(imageName).removeAfterRun().execute();

        //VERIFY
        assertNotNull(container);
        verify(commandLine).run(eq(imageName), argThat(containsInOrder("--rm", "-P", "--name", imageName)));
        verify(commandLine, never()).kill(anyString());
        verify(commandLine, never()).rm(anyString());
    }

    @Test(expected = DockerRuntimeException.class)
    public void run_timeoutDockerStatus() {
        //SETUP
        DockerClient.DOCKER_RUN_TIMEOUT_MILLISECONDS = 500;
        String imageName = "test-image";
        when(commandLine.status(anyString())).thenReturn("");

        //RUN
        dockerClient.run(imageName).execute();
    }

    @Test
    public void killContainer_doNotRemove() {
        //SETUP
        String imageName = "test-image";
        DockerContainer container = dockerClient.run(imageName).removeAfterRun().execute();

        //RUN
        dockerClient.killContainer(container, false);

        //VERIFY
        verify(commandLine).kill(eq(imageName));
        verify(commandLine, never()).rm(anyString());
    }

    @Test
    public void killContainer_doRemove() {
        //SETUP
        String imageName = "test-image";
        DockerContainer container = dockerClient.run(imageName).removeAfterRun().execute();

        //RUN
        dockerClient.killContainer(container, true);

        //VERIFY
        verify(commandLine).kill(eq(imageName));
        verify(commandLine).rm(eq(imageName));
    }

    @Test
    public void close_twoContainersRunning() {
        //SETUP
        dockerClient.run("test-1").execute();
        dockerClient.run("test-2").execute();

        //RUN
        dockerClient.close();

        //VERIFY
        verify(commandLine).kill(eq("test-1"));
        verify(commandLine).kill(eq("test-2"));
        verify(commandLine).rm(eq("test-1"));
        verify(commandLine).rm(eq("test-2"));
    }

    @Test
    public void close_noContainersRunning() {
        //RUN
        dockerClient.close();

        //VERIFY
        verify(commandLine, never()).kill(anyString());
        verify(commandLine, never()).rm(anyString());
    }

    @Test
    public void user_explicit() {
        String user = "Odo";
        String image = "test-image";

        //RUN
        DockerContainer container = dockerClient.run(image).user(user).execute();

        //VERIFY
        assertNotNull(container);
        verify(commandLine).run(anyString(), argThat(containsInOrder("--user", user, "-P", "--name", image)));
    }

    @Test
    public void user_auto() {
        //RUN
        DockerContainer container = dockerClient.run("test-image").currentUser().execute();

        //VERIFY
        assertNotNull(container);
    }

    private static <T> ArgumentMatcher<List<T>> containsInOrder(final T... items) {
        return o -> {
            Iterator<T> itExpected = Arrays.asList(items).iterator();
            Iterator<T> itActual = o.iterator();
            while (itExpected.hasNext() && itActual.hasNext()) {
                if (!itExpected.next().equals(itActual.next())) {
                    return false;
                }
            }
            return !itExpected.hasNext() && !itActual.hasNext();
        };
    }

}