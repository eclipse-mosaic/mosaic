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

package org.eclipse.mosaic.starter.config;

import org.eclipse.mosaic.rti.api.parameters.FederatePriority;

import java.util.ArrayList;
import java.util.List;

public class CRuntime {

    /**
     * The number of threads to be used by the time management implementation.
     * <p>
     * Be careful with this. In most of the cases increasing the number of threads would not improve simulation time,
     * as work is only distributed amongst workers for events with equal time stamps.
     * Furthermore, determinism e.g. by setting a fixed random seed cannot be guaranteed anymore if using
     * more than one thread.
     * </p>
     */
    public int threads = 1;


    public List<CFederate> federates = new ArrayList<>();

    public static class CFederate {

        public String id;

        public String classname;

        public String configuration;

        public String configurationDeployPath;

        public byte priority = FederatePriority.DEFAULT;
        
        public boolean preemptiveExecution = false;

        public String dockerImage;

        public String host = "local";

        public int port = 0;
        public boolean deploy = false;
        public boolean start = false;

        public List<String> subscriptions = new ArrayList<>();

        public String javaCustomArgument;
        public Integer javaMemorySizeXmx;

        public List<String> javaClasspathEntries = new ArrayList<>();
    }

}
