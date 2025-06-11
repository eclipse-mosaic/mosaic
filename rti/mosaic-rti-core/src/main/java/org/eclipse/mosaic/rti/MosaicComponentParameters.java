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

package org.eclipse.mosaic.rti;

public class MosaicComponentParameters {

    /**
     * unique string identifying a federation.
     */
    private String federationId;

    /**
     * end time of a federation execution.
     */
    private long endTime;

    /**
     * Random seed for initializing the random number generator provided by the RTI.
     */
    private Long randomSeed;

    /**
     * The realtime break to slow down simulation for debugging/visualization purposes.
     */
    private double realTimeBreak = 0;

    /**
     * Defines the number of threads to use when executing time advance of ambassadors
     */
    private int numberOfThreads = 1;

    public String getFederationId() {
        return federationId;
    }

    public MosaicComponentParameters setFederationId(String federationId) {
        this.federationId = federationId;
        return this;
    }

    public long getEndTime() {
        return endTime;
    }

    public MosaicComponentParameters setEndTime(long endTime) {
        this.endTime = endTime;
        return this;
    }

    public double getRealTimeBreak() {
        return realTimeBreak;
    }

    public MosaicComponentParameters setRealTimeBreak(double realTimeBreak) {
        this.realTimeBreak = realTimeBreak;
        return this;
    }

    public Long getRandomSeed() {
        return randomSeed;
    }

    public MosaicComponentParameters setRandomSeed(Long randomSeed) {
        this.randomSeed = randomSeed;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public MosaicComponentParameters setNumberOfThreads(int threads) {
        numberOfThreads = threads;
        return this;
    }

}
