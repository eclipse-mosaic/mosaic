/*
 * Copyright (c) 2021 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.fed.sumo.ambassador;

import org.eclipse.mosaic.fed.sumo.bridge.LibSumoBridge;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;
import org.eclipse.mosaic.rti.config.CLocalHost;

import com.google.common.collect.Iterables;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Paths;

/**
 * Implementation of the bridge between MOSAIC and SUMO using the native libsumojni binding provided with SUMO.
 */
public class LibSumoAmbassador extends SumoAmbassador {

    private static final String VALID_LIBSUMO_VERSIONS = "1\\.(23)\\.";

    public LibSumoAmbassador(AmbassadorParameter ambassadorParameter) {
        super(ambassadorParameter);
    }

    @Override
    protected void startSumoLocal() throws InternalFederateException {
        // nop
    }

    @Override
    public void connectToFederate(String host, int port) {
        // nop
    }

    @Override
    public void connectToFederate(String host, InputStream in, InputStream err) throws InternalFederateException {
        // nop
    }

    @Override
    protected void initSumoConnection() throws InternalFederateException {
        if (bridge != null) {
            return;
        }

        CLocalHost.OperatingSystem operatingSystem = CLocalHost.OperatingSystem.getSystemOperatingSystem();

        final String libsumoLibrary;
        if (operatingSystem == CLocalHost.OperatingSystem.WINDOWS) {
            libsumoLibrary = getFromSumoHome("libsumojni.dll");
        } else {
            libsumoLibrary = getFromSumoHome("liblibsumojni.so");
        }

        if (new File(libsumoLibrary).exists()) {
            try {
                System.load(libsumoLibrary);
            } catch (UnsatisfiedLinkError e) {
                throw new InternalFederateException("""
                        The required libsumojni library could be found, but not loaded due to missing dependencies.
                        Make sure your PATH variable contains the entry "$SUMO_HOME/bin".
                        """);
            }

            if (incorrectLibSumoVersion()) {
                throw new InternalFederateException("""
                        The loaded libsumojni library at %s is not compatible with this ambassador. Valid versions are: %s
                        """.formatted(libsumoLibrary, VALID_LIBSUMO_VERSIONS));
            }
        } else {
            try {
                // if no file found, try to load libsumo it directly from java.library.path
                System.loadLibrary("libsumojni");
            } catch (Throwable e) {
                throw new InternalFederateException("""
                        The required libsumojni library could not be found in %s.
                        Make sure SUMO_HOME is set properly and that your SUMO installation contains the libsumojni library.
                        Furthermore, your PATH variable should contain the entry $SUMO_HOME/bin.
                        """.formatted(libsumoLibrary));
            }
        }

        try {
            // make absolute path to configuration file so libsumo is able to find it
            sumoConfig.sumoConfigurationFile = Paths.get(descriptor.getHost().workingDirectory)
                    .resolve(descriptor.getId())
                    .resolve(sumoConfig.sumoConfigurationFile)
                    .toRealPath().toAbsolutePath().toString();
        } catch (IOException e) {
            throw new InternalFederateException("Could not load sumo configuration file", e);
        }

        bridge = new LibSumoBridge(sumoConfig, getProgramArguments(0));
    }

    private void loadQuiet(String libraryFile) {
        try {
            System.load(libraryFile);
        } catch (Throwable error) {
            // quiet
        }
    }

    private void loadLibraryQuiet(String libraryName) {
        try {
            System.loadLibrary(libraryName);
        } catch (Throwable error) {
            // quiet
        }
    }

    public static boolean incorrectLibSumoVersion() {
        try {
            Process p = new ProcessBuilder(getFromSumoHome("sumo"), "--version").start();
            String sumoOutput = Iterables.getFirst(IOUtils.readLines(p.getInputStream(), Charset.defaultCharset()), null);
            return sumoOutput == null || !sumoOutput.matches(".*(" + VALID_LIBSUMO_VERSIONS + ").*");
        } catch (IOException e) {
            return true;
        }
    }
}
