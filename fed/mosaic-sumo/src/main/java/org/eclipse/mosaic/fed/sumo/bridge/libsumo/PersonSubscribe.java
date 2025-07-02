package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

public class PersonSubscribe implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonSubscribe {
    @Override
    public void execute(Bridge bridge, String personId, long startTime, long endTime) throws CommandException, InternalFederateException {
        if (!SimulationSimulateStep.VEHICLE_SUBSCRIPTIONS.contains(personId)) {
            SimulationSimulateStep.VEHICLE_SUBSCRIPTIONS.add(personId);
        }
    }
}
