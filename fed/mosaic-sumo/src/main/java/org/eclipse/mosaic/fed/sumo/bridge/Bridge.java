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

package org.eclipse.mosaic.fed.sumo.bridge;

import org.eclipse.mosaic.fed.sumo.bridge.facades.*;
import org.eclipse.mosaic.fed.sumo.bridge.traci.AbstractTraciCommand;
import org.eclipse.mosaic.fed.sumo.util.MosaicConformUnitIdTransformer;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.lib.util.objects.IdTransformer;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Interface of the bridge connection to SUMO (either TraCI or libsumo).
 */
public interface Bridge {

    /**
     * Defines the {@link IdTransformer} which transforms the vehicle IDs if required. Every
     * {@link AbstractTraciCommand} which reads or writes a vehicle ID, uses this transformer. Per default
     * no transformation is done, i.e. passing the vehicle IDs as is. However, for other scenarios
     * a custom transformer may be defined here, which converts the vehicle IDs known by SUMO to
     * vehicle IDs known by the consumer (e.g. MOSAIC) of this Bridge implementation.
     */
    IdTransformer<String, String> VEHICLE_ID_TRANSFORMER = new MosaicConformUnitIdTransformer(UnitNameGenerator::nextVehicleName);

    /**
     * Defines the {@link IdTransformer} which transforms the person IDs if required.
     */
    IdTransformer<String, String> PERSON_ID_TRANSFORMER = new MosaicConformUnitIdTransformer(UnitNameGenerator::nextAgentName);

    /**
     * Getter for the input stream of the socket connection to SUMO.
     *
     * @return the input stream from the server for reading.
     */
    DataInputStream getIn();

    /**
     * Getter for the output stream of the socket connection to SUMO.
     *
     * @return the output stream towards the server for writing.
     */
    DataOutputStream getOut();

    /**
     * Getter for the currently running SUMO version.
     *
     * @return the SUMO Version of the TraCI server.
     */
    SumoVersion getCurrentVersion();

    /**
     * Getter for the command register.
     *
     * @return the register where command instances are stored.
     */
    CommandRegister getCommandRegister();

    /**
     * Returns a facade which offers methods to control the simulation.
     */
    SimulationFacade getSimulationControl();

    /**
     * Returns a facade which offers methods to control the vehicles in the simulation.
     */
    VehicleFacade getVehicleControl();

    /**
     * Returns a facade which offers methods to control the persons in the simulation.
     */
    PersonFacade getPersonControl();

    /**
     * Returns a facade which offers methods to control traffic lights in the simulation.
     */
    TrafficLightFacade getTrafficLightControl();

    /**
     * Returns a facade which offers methods to control routes taken by vehicles in the simulation.
     */
    RouteFacade getRouteControl();

    /**
     * Returns a facade which offers methods to add POIs to the SUMO GUI and change its properties.
     */
    PoiFacade getPoiControl();

    /**
     * Closes the simulation.
     */
    void close();

    /**
     * Call this method if the connection to SUMO should be closed immediately.
     */
    void emergencyExit(Throwable e);

    void onCommandCompleted();
}
