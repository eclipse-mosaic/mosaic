package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

import java.util.List;

public class SimulationGetArrivedPersonIds implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetArrivedPersonIds {
    @Override
    public List<String> execute(Bridge bridge) throws CommandException, InternalFederateException {
        final StringVector arrivedIds = Simulation.getDepartedPersonIDList();
        try {
            return arrivedIds.stream()
                    .map(Bridge.PERSON_ID_TRANSFORMER::fromExternalId)
                    .toList();
        } finally {
            arrivedIds.delete();
        }
    }
}
