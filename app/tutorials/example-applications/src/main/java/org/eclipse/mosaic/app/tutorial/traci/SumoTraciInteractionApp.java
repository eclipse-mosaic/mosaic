
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

package org.eclipse.mosaic.app.tutorial.traci;

import org.eclipse.mosaic.fed.application.app.AbstractApplication;
import org.eclipse.mosaic.fed.application.app.api.MosaicApplication;
import org.eclipse.mosaic.fed.application.app.api.VehicleApplication;
import org.eclipse.mosaic.fed.application.app.api.os.VehicleOperatingSystem;
import org.eclipse.mosaic.interactions.application.ApplicationInteraction;
import org.eclipse.mosaic.lib.objects.traffic.SumoTraciResult;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleData;
import org.eclipse.mosaic.lib.util.scheduling.Event;

import org.apache.commons.lang3.Validate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This application shows how to interact with SUMO through the interface
 * provided by MOSAIC which allows sending messages to TraCI and reacting on received TraCI response.
 */
public class SumoTraciInteractionApp extends AbstractApplication<VehicleOperatingSystem> implements VehicleApplication, MosaicApplication {

    private long sendTimes = 3; // Contact sumo 3 times
    private String lastSentMsgId;

    /**
     * Convenience container to retrieve info from sumo speed response.
     */
    record SpeedResponse(String vehicleId, double speed) {}

    /**
     * The "meat" of this application. Here a byte array is assembled
     * that follows the traci protocol. With the byte array constructed
     * here, we instruct sumo directly to give us the speed for the
     * provided vehicle. <a href="https://sumo.dlr.de/wiki/TraCI/Vehicle_Value_Retrieval">Vehicle_Value_Retrieval</a>
     *
     * @param vehicleId Vehicle to get the speed from
     * @return The byte array to be sent to sumo
     */
    private byte[] assembleTraciCommand(String vehicleId) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(baos);
        final byte TRACI_VEHICLE = (byte) 0xa4;
        final byte TRACI_GET_SPEED = (byte) 0x40;

        try {
            dos.writeByte(TRACI_VEHICLE); // Bits Traci Command (0xa4 for vehicle value retrieval)
            dos.writeByte(TRACI_GET_SPEED); // Bits Traci Variable Identifier (0x40 for Speed)
            dos.writeInt(vehicleId.length()); // Length of Vehicle Identifier
            dos.write(vehicleId.getBytes(StandardCharsets.UTF_8)); // Vehicle Identifier
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public void onVehicleUpdated(@Nullable VehicleData previousVehicleData, @Nonnull VehicleData updatedVehicleData) {
        if (--sendTimes < 0) { // Contacted sumo 3 times already
            return;
        }
        final byte[] traciMsg = assembleTraciCommand(getOs().getId()); // assemble the TraCI msg for sumo

        lastSentMsgId = getOs().sendSumoTraciRequest(traciMsg);
    }

    /**
     * This method prints out the response given by Sumo, in this case the speed
     * of the vehicle this application runs on.
     *
     * @param sumoTraciResult the response container.
     */
    @Override
    public void onSumoTraciResponded(SumoTraciResult sumoTraciResult) {
        if (sumoTraciResult.getRequestCommandId().equals(lastSentMsgId)) {
            final SpeedResponse speedResponse = decodeGetSpeedResponse(sumoTraciResult.getTraciCommandResult());
            getLog().infoSimTime(
                    this,
                    "Received TraCI message from Sumo. Speed of vehicle {} is {} m/s",
                    speedResponse.vehicleId(),
                    speedResponse.speed()
            );
        }
    }

    private byte[] readBytes(final DataInputStream in, final int bytes) throws IOException {
        final byte[] result = new byte[bytes];

        in.read(result, 0, bytes);

        return result;
    }

    private String readString(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        final byte[] bytes = readBytes(in, length);

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private SpeedResponse decodeGetSpeedResponse(final byte[] msg) {
        final ByteArrayInputStream bais = new ByteArrayInputStream(msg);
        final DataInputStream dis = new DataInputStream(bais);

        try {
            int response = dis.readUnsignedByte(); // should be 0xb4 for response vehicle variable
            Validate.isTrue(response == 0xb4);
            int command = dis.readUnsignedByte(); // should be 0x40 for speed response
            Validate.isTrue(command == 0x40);
            String vehicleId = readString(dis); // vehicle for which the response is
            int variableType = dis.readUnsignedByte(); // type of response, should be 0x0b for double
            Validate.isTrue(variableType == 0x0b);
            double speed = dis.readDouble(); // the actual value, speed in m/s here

            return new SpeedResponse(vehicleId, speed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onInteractionReceived(ApplicationInteraction applicationInteraction) {

    }

    @Override
    public void onStartup() {
        getLog().infoSimTime(this, "Startup");
    }

    @Override
    public void onShutdown() {
        getLog().infoSimTime(this, "Shutdown");
    }

    @Override
    public void processEvent(Event event) throws Exception {

    }
}
