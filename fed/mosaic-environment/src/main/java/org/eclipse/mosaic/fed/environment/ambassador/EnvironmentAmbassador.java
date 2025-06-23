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

package org.eclipse.mosaic.fed.environment.ambassador;

import org.eclipse.mosaic.fed.environment.config.CEnvironment;
import org.eclipse.mosaic.fed.environment.config.CEvent;
import org.eclipse.mosaic.fed.environment.config.CEventTime;
import org.eclipse.mosaic.interactions.environment.EnvironmentSensorActivation;
import org.eclipse.mosaic.interactions.environment.EnvironmentSensorUpdates;
import org.eclipse.mosaic.interactions.environment.GlobalEnvironmentUpdates;
import org.eclipse.mosaic.interactions.traffic.VehicleUpdates;
import org.eclipse.mosaic.lib.objects.environment.EnvironmentEvent;
import org.eclipse.mosaic.lib.objects.environment.EnvironmentEventLocation;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.objects.ObjectInstantiation;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.AbstractFederateAmbassador;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.Interaction;
import org.eclipse.mosaic.rti.api.InternalFederateException;
import org.eclipse.mosaic.rti.api.RtiAmbassador;
import org.eclipse.mosaic.rti.api.parameters.AmbassadorParameter;

import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main class for the environment simulator that emits events to vehicles that lie in their
 * configured time span and the configured event area.
 */
public class EnvironmentAmbassador extends AbstractFederateAmbassador {

    private final CEnvironment config;

    /**
     * The list of units (e.g. vehicles) which are interested in environment event updates.
     */
    private final Set<String> activeUnits = new HashSet<>();

    /**
     * The configuration file referenced in {@link AmbassadorParameter} is used for {@link CEnvironment}
     * object instantiation. Log message from object instantiation is logged.
     *
     * @param ambassadorParameter an {@link AmbassadorParameter} object with ambassador id, logger id and federate configuration.
     * @throws RuntimeException if configuration object could not be instantiated.
     */
    public EnvironmentAmbassador(AmbassadorParameter ambassadorParameter) throws RuntimeException {
        super(ambassadorParameter);

        try {
            config = new ObjectInstantiation<>(CEnvironment.class, log).readFile(ambassadorParameter.configuration);
        } catch (InstantiationException e) {
            throw new RuntimeException("Configuration object could not be instantiated: ", e);
        }
    }

    /**
     * Calls the {@link RtiAmbassador#requestAdvanceTime(long)}.
     *
     * @param startTime Start time of the simulation run in nano seconds.
     * @param endTime   End time of the simulation run in nano seconds.
     */
    @Override
    public void initialize(long startTime, long endTime) {
        try {
            rti.requestAdvanceTime(startTime + TIME.SECOND);
        } catch (IllegalValueException e) {
            log.error("First time advance could not be requested", e);
        }
    }

    /**
     * Creates a list with {@link EnvironmentEventLocation}s and adds to it all events that have
     * their area set. Calculates the closest possible point in time for a request to advance time toward it.
     *
     * @param time The timestamp towards which the federate can advance it local
     *             time.
     */
    @Override
    protected void processTimeAdvanceGrant(long time) throws InternalFederateException {
        super.processTimeAdvanceGrant(time);

        long nextTimeAdvanceGrant = Long.MAX_VALUE;

        List<EnvironmentEventLocation> currentEvents = new ArrayList<>();

        for (CEvent event : config.events) {
            Validate.notNull(event.location, "No location given for event");
            Validate.notNull(event.time, "No time window given for event");
            Validate.notNull(event.event, "No event type description given for event");

            if (isInTimeFrame(event.time, time)) {
                if (event.location.area != null) {
                    currentEvents.add(new EnvironmentEventLocation(event.location.area, event.event.type));
                }
            }
            if (event.time.start > time) {
                nextTimeAdvanceGrant = Math.min(nextTimeAdvanceGrant, event.time.start);
            }
            if (event.time.end > time) {
                nextTimeAdvanceGrant = Math.min(nextTimeAdvanceGrant, event.time.end);
            }
        }

        if (nextTimeAdvanceGrant < Long.MAX_VALUE) {
            try {
                rti.requestAdvanceTime(nextTimeAdvanceGrant);
            } catch (IllegalValueException e) {
                log.error("Next time advance could not be requested", e);
            }
        }

        try {
            rti.triggerInteraction(new GlobalEnvironmentUpdates(time, currentEvents));
        } catch (IllegalValueException e) {
            log.error("Could not send message about current events");
        }
    }

    @Override
    public void processInteraction(Interaction interaction) {
        if (interaction.getTypeId().equals(VehicleUpdates.TYPE_ID)) {
            emitSensorData((VehicleUpdates) interaction);
        } else if (interaction.getTypeId().equals(EnvironmentSensorActivation.TYPE_ID)) {
            final EnvironmentSensorActivation tmpInteraction = (EnvironmentSensorActivation) interaction;

            log.info("Registered for sensor information: id={} at time {}", tmpInteraction.getVehicleId(), TIME.format(interaction.getTime()));
            activeUnits.add(tmpInteraction.getVehicleId());
        } else {
            log.warn("SimTime {}: Unknown message received: {}", interaction.getTypeId(), TIME.format(interaction.getTime()));
        }
    }

    /**
     * Emits sensor data.
     *
     * @param vehicleUpdates VehicleUpdates to get time, {@link VehicleData}s and next update time from
     * @throws RuntimeException if an interaction with sensor data could not be sent.
     */
    private void emitSensorData(VehicleUpdates vehicleUpdates) {
        final long startTime = vehicleUpdates.getTime();
        final long endTime = vehicleUpdates.getNextUpdate();

        log.debug("Received {} updated vehicle movements", vehicleUpdates.getUpdated().size());

        for (VehicleData info : vehicleUpdates.getUpdated()) {
            final List<EnvironmentEvent> events = config.events.stream()
                    .filter(e -> isValidEvent(e, info))
                    .map(e -> new EnvironmentEvent(e.event.type, e.event.value, startTime, endTime))
                    .toList();

            if (!events.isEmpty()) {
                try {
                    rti.triggerInteraction(new EnvironmentSensorUpdates(vehicleUpdates.getTime(), info.getName(), events));
                    if (log.isDebugEnabled()) {
                        log.debug("SimTime {}: Emitted sensor data to vehicle {}", info.getTime(), info.getName());
                    }
                } catch (IllegalValueException | InternalFederateException e) {
                    throw new RuntimeException("Could not send interaction: " + e.getMessage());
                }
            }
        }
        // Delete vehicles that have left the simulation
        for (String id : vehicleUpdates.getRemovedNames()) {
            log.debug("Removed {} from monitoring list.", id);
            activeUnits.remove(id);
        }
    }

    /**
     * Checks if the event is valid based upon following criteria:
     * 1. Is vehicle monitored for events?
     * 2. Does the event fit into current time frame?
     * 3. Is the vehicle in the area of the event?
     *
     * @param event       The event to check against
     * @param vehicleData The vehicle for the event
     * @return true if the event is valid according to criteria above
     */
    private boolean isValidEvent(CEvent event, VehicleData vehicleData) {
        return event.location != null && event.time != null && event.event != null
                && isMonitored(vehicleData.getName())
                && isInTimeFrame(event.time, vehicleData.getTime())
                && isInEventArea(event, vehicleData);
    }

    /**
     * Returns true if the list of monitored vehicles contains a vehicle with the given name.
     *
     * @param vehicleName vehicle name
     * @return true if the list of monitored vehicles contains a vehicle with the given name
     */
    private boolean isMonitored(String vehicleName) {
        return activeUnits.contains(vehicleName);
    }

    static boolean isInTimeFrame(CEventTime eventTimeFrame, long messageTime) {
        return (messageTime >= eventTimeFrame.start && messageTime < eventTimeFrame.end);
    }

    private static boolean isInEventArea(CEvent event, VehicleData vehicleData) {
        if (event.location.area != null) {
            return event.location.area.contains(vehicleData.getPosition());
        }
        if (event.location.connectionId != null) {
            return event.location.connectionId.equals(vehicleData.getRoadPosition().getConnection().getId());
        }
        return false;
    }

    @Override
    public boolean isTimeConstrained() {
        return false;
    }

    @Override
    public boolean isTimeRegulating() {
        return false;
    }
}
