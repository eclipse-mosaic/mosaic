package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.eclipse.sumo.libsumo.Simulation;
import org.eclipse.sumo.libsumo.StringVector;

import java.util.List;

public class SimulationGetDepartedPersonIds implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationGetDepartedPersonIds {
    @Override
    public List<String> execute(Bridge bridge) throws CommandException, InternalFederateException {
        final StringVector departedIds = Simulation.getDepartedPersonIDList();
        try {
            return departedIds.stream()
                    .map(Bridge.PERSON_ID_TRANSFORMER::fromExternalId)
                    .toList();
        } finally {
            departedIds.delete();
        }
    }
}
