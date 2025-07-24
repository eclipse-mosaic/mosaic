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

package org.eclipse.mosaic.lib.objects.trafficlight;

import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.objects.ToDataOutput;
import org.eclipse.mosaic.lib.util.SerializationUtils;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Class represents a group of traffic lights that together control a junction.
 */
public class TrafficLightGroup implements Serializable, ToDataOutput {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Identifier of this group.
     */
    private final String groupId;

    /**
     * Traffic light programs that are configured and available for this traffic light group.
     */
    private final Map<String, TrafficLightProgram> programs;

    /**
     * List of traffic lights within this group.
     */
    private final List<TrafficLight> trafficLights;

    /**
     * Constructor, which initializes the main variables.
     *
     * @param id            TrafficLightGroup identifier
     * @param programs      available programs that were defined in the configuration file
     *                      (e.g. for SUMO in scenarioname.net.xml)
     * @param trafficLights traffic light this group consists of
     */
    public TrafficLightGroup(String id, @Nonnull Map<String, TrafficLightProgram> programs, List<TrafficLight> trafficLights) {
        this.groupId = id;
        this.programs = Collections.unmodifiableMap(new HashMap<>(programs));
        this.trafficLights = Collections.unmodifiableList(new ArrayList<>(trafficLights));
    }

    /**
     * Constructs a TrafficLightGroup object from the given data input.
     *
     * @param dataInput data input to construct the TrafficLightGroup object from
     * @throws InternalFederateException if it wasn't possible to construct the object from given data input
     */
    public TrafficLightGroup(DataInput dataInput) throws InternalFederateException {
        try {
            String trafficLightGroupId = dataInput.readUTF();

            List<TrafficLight> trafficLightList = new ArrayList<>();
            int trafficLightAmount = dataInput.readInt();

            for (int i = 0; i < trafficLightAmount; i++) {

                int trafficLightIndex = dataInput.readInt();
                GeoPoint geoPosition = SerializationUtils.decodeGeoPoint(dataInput);

                String incomingLane = dataInput.readUTF();
                String outgoingLane = dataInput.readUTF();

                boolean isRed = dataInput.readBoolean();
                boolean isYellow = dataInput.readBoolean();
                boolean isGreen = dataInput.readBoolean();
                TrafficLightState trafficLightState = new TrafficLightState(isRed, isGreen, isYellow);

                TrafficLight trafficLight = new TrafficLight(trafficLightIndex, geoPosition, incomingLane, outgoingLane, trafficLightState);
                trafficLightList.add(trafficLight);

            } //end of for-loop with traffic lights

            Map<String, TrafficLightProgram> trafficLightPrograms = new LinkedHashMap<>();
            int programAmount = dataInput.readInt();
            for (int i = 0; i < programAmount; i++) {
                String trafficLightProgramId = dataInput.readUTF();

                List<TrafficLightProgramPhase> trafficLightProgramPhases = new ArrayList<>();
                int phasesAmount = dataInput.readInt();
                for (int ph = 0; ph < phasesAmount; ph++) {

                    int phaseIndex = dataInput.readInt();
                    long configuredDuration = dataInput.readLong();
                    long remainingDuration = dataInput.readLong();

                    List<TrafficLightState> trafficLightStates = new ArrayList<>();
                    for (int st = 0; st < trafficLightAmount; st++) {

                        boolean isRed = dataInput.readBoolean();
                        boolean isYellow = dataInput.readBoolean();
                        boolean isGreen = dataInput.readBoolean();
                        TrafficLightState state = new TrafficLightState(isRed, isGreen, isYellow);

                        trafficLightStates.add(state);
                    } //end of for-loop for traffic light states within a traffic light program phase

                    TrafficLightProgramPhase phase = new TrafficLightProgramPhase(phaseIndex, configuredDuration, trafficLightStates);
                    phase.setRemainingDuration(remainingDuration);
                    trafficLightProgramPhases.add(phase);

                } //end of for-loop with traffic light program phases

                int currentPhaseIndex = dataInput.readInt();
                TrafficLightProgram program = new TrafficLightProgram(trafficLightProgramId, trafficLightProgramPhases, currentPhaseIndex);
                trafficLightPrograms.put(trafficLightProgramId, program);
            } //end of for-loop with traffic light programs

            this.groupId = trafficLightGroupId;
            this.programs = trafficLightPrograms;
            this.trafficLights = trafficLightList;

        } catch (IOException e) {
            throw new InternalFederateException("Could not construct a TrafficLightGroup object from given data input", e);
        }
    }

    /**
     * Returns the id of the group.
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * Returns the list of all signals.
     */
    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }

    public Map<String, TrafficLightProgram> getPrograms() {
        return programs;
    }

    public TrafficLightProgram getProgramById(String newProgramId) {
        return programs.get(newProgramId);
    }

    /**
     * Returns geo position of the first traffic light in the traffic light group.
     */
    public GeoPoint getFirstPosition() {
        return getTrafficLights().get(0).getPosition();
    }

    @Override
    public void toDataOutput(DataOutput dataOutput) throws IOException {
        dataOutput.writeUTF(getGroupId());

        //write signal count
        final List<TrafficLight> trafficLightList = getTrafficLights();
        dataOutput.writeInt(trafficLightList.size());

        for (TrafficLight signal : trafficLightList) {

            //write traffic light (signal) id
            dataOutput.writeInt(signal.getIndex());

            //write geo position
            SerializationUtils.encodeGeoPoint(dataOutput, signal.getPosition());

            //write incoming and outgoing lanes
            dataOutput.writeUTF(signal.getIncomingLane());
            dataOutput.writeUTF(signal.getOutgoingLane());

            //write the current state of the traffic light
            dataOutput.writeBoolean(signal.getCurrentState().isRed());
            dataOutput.writeBoolean(signal.getCurrentState().isYellow());
            dataOutput.writeBoolean(signal.getCurrentState().isGreen());
        }

        //write the amount of traffic light programs available for this group
        dataOutput.writeInt(programs.size());

        for (TrafficLightProgram trafficLightProgram : programs.values()) {

            dataOutput.writeUTF(trafficLightProgram.getProgramId());
            dataOutput.writeInt(trafficLightProgram.getPhases().size());

            for (TrafficLightProgramPhase phase : trafficLightProgram.getPhases()) {
                dataOutput.writeInt(phase.getIndex());
                dataOutput.writeLong(phase.getConfiguredDuration());
                dataOutput.writeLong(phase.getRemainingDuration());

                for (TrafficLightState state : phase.getStates()) {
                    dataOutput.writeBoolean(state.isRed());
                    dataOutput.writeBoolean(state.isYellow());
                    dataOutput.writeBoolean(state.isGreen());
                }
            }

            dataOutput.writeInt(trafficLightProgram.getCurrentPhaseIndex());
        }

    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(7, 17)
                .append(groupId)
                .append(trafficLights)
                .append(programs)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        TrafficLightGroup other = (TrafficLightGroup) obj;
        return new EqualsBuilder()
                .append(this.groupId, other.groupId)
                .append(this.trafficLights, other.trafficLights)
                .append(this.programs, other.programs)
                .isEquals();
    }

    @Override
    public String toString() {
        return "TrafficLightGroup{"
                + "groupId=" + groupId
                + ", trafficLights="
                + trafficLights + ", trafficLightPrograms="
                + programs + '}';
    }

}

