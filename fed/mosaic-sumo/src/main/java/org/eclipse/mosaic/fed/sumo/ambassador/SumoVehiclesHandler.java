/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

import org.eclipse.mosaic.fed.sumo.bridge.traci.VehicleSetRemove;
import org.eclipse.mosaic.fed.sumo.util.SumoVehicleClassMapping;
import org.eclipse.mosaic.interactions.mapping.VehicleRegistration;
import org.eclipse.mosaic.interactions.mapping.advanced.ScenarioVehicleRegistration;
import org.eclipse.mosaic.interactions.traffic.VehicleTypesInitialization;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.interactions.vehicle.VehicleFederateAssignment;
import org.eclipse.mosaic.interactions.vehicle.VehicleParametersChange;
import org.eclipse.mosaic.interactions.vehicle.VehicleRouteRegistration;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.objects.mapping.VehicleMapping;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleParameter;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleRoute;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import com.google.common.collect.Iterables;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * All the ambassador's logic which takes care of managing vehicles, such as adding, subscribing, deleting.
 */
public class SumoVehiclesHandler extends AbstractHandler {

    /**
     * List of vehicles that are simulated externally.
     */
    final Map<String, ExternalVehicleState> externalVehicles = new HashMap<>();

    /**
     * Set containing all vehicles, that have been added from the RTI e.g. using the Mapping file.
     */
    final Set<String> vehiclesAddedViaRti = new HashSet<>();

    /**
     * Set containing all vehicles, that have been added using the SUMO route file.
     */
    final Set<String> vehiclesAddedViaRouteFile = new HashSet<>();

    /**
     * Cached {@link VehicleRegistration}-interactions, which will clear vehicles if they can be added and try again
     * next time step if it couldn't be emitted.
     */
    private final List<VehicleRegistration> notYetAddedVehicles = new ArrayList<>();

    /**
     * Cached {@link VehicleRegistration}-interactions, for vehicles, that haven't been subscribed to yet.
     */
    private final List<VehicleRegistration> notYetSubscribedVehicles = new ArrayList<>();

    private final Map<String, VehicleType> vehicleTypes = new HashMap<>();

    private final SumoRoutesHandler routesHandler;

    SumoVehiclesHandler(SumoRoutesHandler routesHandler) {
        this.routesHandler = routesHandler;
    }

    synchronized void handleVehicleTypesInitialization(VehicleTypesInitialization vehicleTypesInitialization) {
        vehicleTypes.putAll(vehicleTypesInitialization.getTypes());
    }

    /**
     * Vehicles of the {@link #notYetSubscribedVehicles} list will be added to simulation by this function
     * or cached again for the next time advance.
     *
     * @param time Current system time
     * @throws InternalFederateException if vehicle couldn't be added
     */
    void flushNotYetAddedVehicles(long time) throws InternalFederateException {
        // if not yet a last advance time was set, sumo is not ready
        if (time < 0) {
            return;
        }
        // now add all vehicles, that were received from RTI
        addNotYetAddedVehicles(time);
        // now subscribe to all relevant vehicles
        subscribeToNotYetSubscribedVehicles(time);
    }

    private void addNotYetAddedVehicles(long time) throws InternalFederateException {
        for (Iterator<VehicleRegistration> iterator = notYetAddedVehicles.iterator(); iterator.hasNext(); ) {
            VehicleRegistration vehicleRegistration = iterator.next();

            String vehicleId = vehicleRegistration.getMapping().getName();
            String vehicleType = vehicleRegistration.getMapping().getVehicleType().getName();
            String routeId = vehicleRegistration.getDeparture().getRouteId();
            String departPos = String.format(Locale.ENGLISH, "%.2f", vehicleRegistration.getDeparture().getDeparturePos());
            int departIndex = vehicleRegistration.getDeparture().getDepartureConnectionIndex();
            String departSpeed = extractDepartureSpeed(vehicleRegistration);
            String laneId = extractDepartureLane(vehicleRegistration);
            ExternalVehicleState externalVehicleState = externalVehicles.get(vehicleId);
            if (externalVehicleState != null) {
                if (externalVehicleState.isAdded()) {
                    iterator.remove();
                    continue;
                }
                // We choose "" as the default routeId because we want it to be possible to spawn vehicles without a defined route.
                // This is especially useful for parking vehicles that are not supposed to move.
                routeId = Iterables.getFirst(routesHandler.routes.keySet(), "");
                laneId = "free";
            }

            try {
                if (vehicleRegistration.getTime() <= time) {
                    log.info("Adding new vehicle \"{}\" at simulation time {} ns (type={}, routeId={}, laneId={}, departPos={})",
                            vehicleId, vehicleRegistration.getTime(), vehicleType, routeId, laneId, departPos);

                    if (!routesHandler.routes.containsKey(routeId) && !routeId.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Unknown route " + routeId + " for vehicle with departure time " + vehicleRegistration.getTime()
                        );
                    }

                    if (departIndex > 0 && !routeId.isEmpty()) {
                        routeId = routesHandler.cutAndAddRoute(routeId, departIndex);
                    }

                    bridge.getSimulationControl().addVehicle(vehicleId, routeId, vehicleType, laneId, departPos, departSpeed);

                    final VehicleType cachedType = vehicleTypes.get(vehicleType);
                    if (cachedType != null) {
                        applyChangesInVehicleTypeForVehicle(vehicleId, vehicleRegistration.getMapping().getVehicleType(), cachedType);
                    } else {
                        log.warn("Unknown vehicle type {}. Ensure that a suitable vType is configured in the SUMO configuration.", vehicleType);
                    }

                    if (externalVehicleState != null) {
                        externalVehicleState.setAdded(true);
                    }
                    iterator.remove();
                }
            } catch (InternalFederateException e) {
                log.warn("Vehicle with id: {} could not be added.({})", vehicleId, e.getClass().getCanonicalName(), e);
                if (sumoConfig.exitOnInsertionError) {
                    throw e;
                }
                iterator.remove();
            }
        }
    }

    private void subscribeToNotYetSubscribedVehicles(long time) throws InternalFederateException {
        for (Iterator<VehicleRegistration> iterator = notYetSubscribedVehicles.iterator(); iterator.hasNext(); ) {
            VehicleRegistration currentVehicleRegistration = iterator.next();
            String vehicleId = currentVehicleRegistration.getMapping().getName();
            if (externalVehicles.containsKey(vehicleId)) {
                iterator.remove();
                continue;
            }
            try {
                // always subscribe to vehicles, that came from SUMO and are in notYetSubscribedVehicles-list
                if (vehiclesAddedViaRouteFile.contains(vehicleId) || currentVehicleRegistration.getTime() <= time) {
                    bridge.getSimulationControl().subscribeForVehicle(vehicleId, currentVehicleRegistration.getTime(), ambassador.getEndTime());
                    iterator.remove();
                }
            } catch (InternalFederateException e) {
                log.warn("Couldn't subscribe to vehicle {}.", vehicleId);
                if (sumoConfig.exitOnInsertionError) {
                    throw e;
                }
                iterator.remove();
            }
        }
    }

    private void applyChangesInVehicleTypeForVehicle(String vehicleId, VehicleType actualVehicleType, VehicleType baseVehicleType) throws InternalFederateException {
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getTau(), baseVehicleType.getTau())) {
            double minReactionTime = sumoConfig.updateInterval / 1000d;
            bridge.getVehicleControl().setReactionTime(
                    vehicleId, Math.max(minReactionTime, actualVehicleType.getTau() + sumoConfig.timeGapOffset)
            );
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getMaxSpeed(), baseVehicleType.getMaxSpeed())) {
            bridge.getVehicleControl().setMaxSpeed(vehicleId, actualVehicleType.getMaxSpeed());
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getAccel(), baseVehicleType.getAccel())) {
            bridge.getVehicleControl().setMaxAcceleration(vehicleId, actualVehicleType.getAccel());
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getDecel(), baseVehicleType.getDecel())) {
            bridge.getVehicleControl().setMaxDeceleration(vehicleId, actualVehicleType.getDecel());
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getMinGap(), baseVehicleType.getMinGap())) {
            bridge.getVehicleControl().setMinimumGap(vehicleId, actualVehicleType.getMinGap());
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getLength(), baseVehicleType.getLength())) {
            bridge.getVehicleControl().setVehicleLength(vehicleId, actualVehicleType.getLength());
        }
        if (!MathUtils.isFuzzyEqual(actualVehicleType.getSpeedFactor(), baseVehicleType.getSpeedFactor())) {
            bridge.getVehicleControl().setSpeedFactor(vehicleId, actualVehicleType.getSpeedFactor());
        }
    }

    private String extractDepartureSpeed(VehicleRegistration vehicleRegistration) {
        return switch (vehicleRegistration.getDeparture().getDepartureSpeedMode()) {
            case PRECISE -> String.format(Locale.ENGLISH, "%.2f", vehicleRegistration.getDeparture().getDepartureSpeed());
            case RANDOM -> "random";
            case MAXIMUM -> "max";
        };
    }

    private String extractDepartureLane(VehicleRegistration vehicleRegistration) {
        switch (vehicleRegistration.getDeparture().getLaneSelectionMode()) {
            case RANDOM -> {
                return "random";
            }
            case FREE -> {
                return "free";
            }
            case ALLOWED -> {
                return "allowed";
            }
            case BEST -> {
                return "best";
            }
            case FIRST -> {
                return "first";
            }
            case HIGHWAY -> {
                return isTruckOrTrailer(vehicleRegistration.getMapping().getVehicleType().getVehicleClass())
                        ? "first"
                        : "best";
            }
            default -> {
                int extractedLaneId = vehicleRegistration.getDeparture().getDepartureLane();
                return extractedLaneId >= 0
                        ? Integer.toString(extractedLaneId)
                        : "best";
            }
        }
    }

    private boolean isTruckOrTrailer(VehicleClass vehicleClass) {
        return SumoVehicleClassMapping.toSumo(vehicleClass).equals("truck")
                || SumoVehicleClassMapping.toSumo(vehicleClass).equals("trailer");
    }

    /**
     * Handles the {@link VehicleRegistration}-registration and adds the vehicle to the current
     * simulation.
     *
     * @param vehicleRegistration {@link VehicleRegistration} containing the vehicle definition.
     */
    void handleRegistration(VehicleRegistration vehicleRegistration) {
        VehicleMapping vehicleMapping = vehicleRegistration.getMapping();
        String vehicleId = vehicleMapping.getName();
        String logMessage;
        boolean isVehicleAddedViaRti = !vehiclesAddedViaRouteFile.contains(vehicleMapping.getName());
        if (isVehicleAddedViaRti) {
            vehiclesAddedViaRti.add(vehicleMapping.getName());
            notYetAddedVehicles.add(vehicleRegistration);
            logMessage = "VehicleRegistration from RTI \"{}\" received at simulation time {} ns (subscribe={})";
        } else { // still subscribe to vehicles with apps
            logMessage = "VehicleRegistration for SUMO vehicle \"{}\" received at simulation time {} ns (subscribe={})";
        }

        boolean subscribeToVehicle = sumoConfig.subscribeToAllVehicles || vehicleMapping.hasApplication();
        log.info(logMessage, vehicleId, vehicleRegistration.getTime(), subscribeToVehicle);
        if (subscribeToVehicle) { // now prepare vehicles to subscribe to
            notYetSubscribedVehicles.add(vehicleRegistration);
        }
    }

    /**
     * Extract data from received {@link VehicleFederateAssignment} interactions and add vehicle to list of externally simulated vehicles.
     *
     * @param vehicleFederateAssignment interaction indicating that a vehicle is simulated externally.
     */
    synchronized void handleFederateAssignment(VehicleFederateAssignment vehicleFederateAssignment) {
        if (!vehicleFederateAssignment.getAssignedFederate().equals(ambassador.getId())
                && !externalVehicles.containsKey(vehicleFederateAssignment.getVehicleId())) {
            externalVehicles.put(vehicleFederateAssignment.getVehicleId(), new ExternalVehicleState());
        }
    }

    /**
     * Extract data from received {@link VehicleUpdates} interaction and apply
     * updates of externally simulated vehicles to SUMO via TraCI calls.
     *
     * @param vehicleUpdates interaction indicating vehicle updates of a simulator
     */
    synchronized void handleExternalVehicleUpdates(VehicleUpdates vehicleUpdates) throws InternalFederateException {
        if (vehicleUpdates == null || vehicleUpdates.getSenderId().equals(ambassador.getId())) {
            return;
        }

        for (VehicleData vehicle : Iterables.concat(vehicleUpdates.getAdded(), vehicleUpdates.getUpdated())) {
            ExternalVehicleState externalVehicleState = externalVehicles.get(vehicle.getName());
            if (externalVehicleState != null) {
                externalVehicleState.setLastMovementInfo(vehicle);
            }
        }

        for (String removed : vehicleUpdates.getRemovedNames()) {
            if (externalVehicles.containsKey(removed)) {
                bridge.getSimulationControl().removeVehicle(removed, VehicleSetRemove.Reason.ARRIVED);
            }
        }
    }

    void propagateSumoVehiclesToRti(long time) throws InternalFederateException {
        List<String> routeFileVehicles = getRouteFileVehicles();
        String vehicleTypeId;
        VehicleType vehicleType;
        for (String vehicleId : routeFileVehicles) {
            vehiclesAddedViaRouteFile.add(vehicleId);
            vehicleTypeId = bridge.getVehicleControl().getVehicleTypeId(vehicleId);
            vehicleType = bridge.getVehicleControl().getVehicleType(vehicleTypeId);
            try {
                rti.triggerInteraction(new ScenarioVehicleRegistration(time, vehicleId, vehicleType));
            } catch (IllegalValueException e) {
                throw new InternalFederateException(e);
            }
            if (sumoConfig.subscribeToAllVehicles) { // this is required as vehicles with no apps can't be subscribed to otherwise
                bridge.getSimulationControl().subscribeForVehicle(vehicleId, time, ambassador.getEndTime());
            }
        }
    }

    void setExternalVehiclesToLatestPositions(long time) {
        for (Map.Entry<String, ExternalVehicleState> external : externalVehicles.entrySet()) {
            final String externalVehicle = external.getKey();
            final ExternalVehicleState externalState = external.getValue();
            if (externalState.isAdded() && externalState.isRequireUpdate(time)) {
                VehicleData latestVehicleData = externalState.getLastMovementInfo();
                if (latestVehicleData == null) {
                    log.warn("No position data available for external vehicle {}", externalVehicle);
                    latestVehicleData = bridge.getSimulationControl().getLastKnownVehicleData(externalVehicle);
                }
                if (latestVehicleData != null) {
                    try {
                        bridge.getVehicleControl().moveToXY(
                                externalVehicle,
                                latestVehicleData.getPosition().toCartesian(),
                                latestVehicleData.getHeading(),
                                sumoConfig.moveToXyMode
                        );
                        externalState.updatedInSumo(time);
                    } catch (InternalFederateException e) {
                        log.warn("Could not set position of vehicle " + external.getKey(), e);
                    }
                }
            }
        }
    }

    void removeExternalVehiclesFromUpdates(VehicleUpdates updates) {
        Iterator<VehicleData> updatesAddedIterator = updates.getAdded().iterator();
        while (updatesAddedIterator.hasNext()) {
            VehicleData currentVehicle = updatesAddedIterator.next();
            if (externalVehicles.containsKey(currentVehicle.getName())) {
                externalVehicles.get(currentVehicle.getName()).setAdded(true);
                updatesAddedIterator.remove();
            }
        }

        updates.getUpdated().removeIf(currentVehicle -> externalVehicles.containsKey(currentVehicle.getName()));
        updates.getRemovedNames().removeIf(vehicle -> externalVehicles.remove(vehicle) != null);
    }

    /**
     * Changes parameters of externally added vehicles.
     * So far only color change is supported.
     *
     * @param vehicleParametersChange Stores a list of vehicle parameters that should be changed.
     * @throws InternalFederateException Throws an IllegalArgumentException if color could not be set correctly.
     */
    void changeExternalParameters(VehicleParametersChange vehicleParametersChange) throws InternalFederateException {
        final String veh_id = vehicleParametersChange.getVehicleId();
        for (final VehicleParameter param : vehicleParametersChange.getVehicleParameters()) {
            // Only color is supported as a parameter for external vehicles so far.
            if (param.getParameterType() == VehicleParameter.VehicleParameterType.COLOR) {
                final Color color = param.getValue();
                bridge.getVehicleControl().setColor(veh_id, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            }
        }
    }

    private List<String> getRouteFileVehicles() throws InternalFederateException {
        return bridge.getSimulationControl().getDepartedVehicles().stream()
                .filter(v -> !vehiclesAddedViaRti.contains(v)) // all vehicles not added via MOSAIC are added by SUMO
                .toList();
    }

}
