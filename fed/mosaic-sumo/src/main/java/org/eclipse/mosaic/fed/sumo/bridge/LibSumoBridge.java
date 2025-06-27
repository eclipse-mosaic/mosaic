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

package org.eclipse.mosaic.fed.sumo.bridge;


import org.eclipse.mosaic.fed.sumo.bridge.facades.PersonFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.PoiFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.RouteFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.SimulationFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.TrafficLightFacade;
import org.eclipse.mosaic.fed.sumo.bridge.facades.VehicleFacade;
import org.eclipse.mosaic.fed.sumo.config.CSumo;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;

/**
 * Implementation of the SumoBridge which uses methods provided by SUMO via JNI.
 * The Jar file which provides the function is shipped with SUMO. This class expects a
 * the libsumojni library (dll or shared object) to be already loaded.
 */
public class LibSumoBridge implements Bridge {

    private final CommandRegister commandRegister;
    private final SumoVersion sumoVersion;

    private final SimulationFacade simulationControl;
    private final TrafficLightFacade trafficLightControl;
    private final VehicleFacade vehicleControl;
    private final RouteFacade routeControl;
    private final PoiFacade poiControl;
    private final PersonFacade personControl;

    /**
     * Constructor for the {@link LibSumoBridge}, initializing simulation and facades based on configuration.
     *
     * @param sumoConfiguration the MOSAIC internal {@link CSumo SUMO configuration}
     * @param parameters        the program arguments for starting the simulation
     */
    public LibSumoBridge(CSumo sumoConfiguration, List<String> parameters) {

        Simulation.load(new StringVector(parameters));

        this.sumoVersion = SumoVersion.UNKNOWN; // not important for this kind of bridge;

        this.commandRegister = new CommandRegister(sumoConfiguration, "libsumo");
        this.commandRegister.setBridge(this);

        this.simulationControl = new SimulationFacade(this, sumoConfiguration);

        this.vehicleControl = new VehicleFacade(this);
        this.trafficLightControl = new TrafficLightFacade(this);
        this.routeControl = new RouteFacade(this);
        this.poiControl = new PoiFacade(this);
        this.personControl = new PersonFacade(this);
    }


    @Override
    public SumoVersion getCurrentVersion() {
        return sumoVersion;
    }

    @Override
    public CommandRegister getCommandRegister() {
        return commandRegister;
    }

    @Override
    public SimulationFacade getSimulationControl() {
        return simulationControl;
    }

    @Override
    public VehicleFacade getVehicleControl() {
        return vehicleControl;
    }

    @Override
    public TrafficLightFacade getTrafficLightControl() {
        return trafficLightControl;
    }

    @Override
    public RouteFacade getRouteControl() {
        return routeControl;
    }

    @Override
    public PoiFacade getPoiControl() {
        return poiControl;
    }

    @Override
    public PersonFacade getPersonControl() {
        return personControl;
    }

    @Override
    public void close() {
        Simulation.close();
    }

    @Override
    public void emergencyExit(Throwable e) {
        // should not be called
    }

    @Override
    public DataInputStream getIn() {
        throw new UnsupportedOperationException("This SUMO bridge implementation does not provide a socket connection.");
    }

    @Override
    public DataOutputStream getOut() {
        throw new UnsupportedOperationException("This SUMO bridge implementation does not provide a socket connection.");
    }

    @Override
    public void onCommandCompleted() {
        //nop
    }
}
