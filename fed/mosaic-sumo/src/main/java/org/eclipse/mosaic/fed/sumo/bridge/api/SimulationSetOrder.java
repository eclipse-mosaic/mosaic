package org.eclipse.mosaic.fed.sumo.bridge.api;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

public interface SimulationSetOrder {
    /**
     * Executes the command in order to set simulation order when multiple clients are to be connected to the simulation.
     *
     * @param bridge Connection to SUMO.
     * @param order  should generally be 1
     * @throws CommandException          if the status code of the response is ERROR. The connection to SUMO is still available.
     * @throws InternalFederateException if some serious error occurs during writing or reading. The connection to SUMO is shut down.
     */
    void execute(Bridge bridge, int order) throws CommandException, InternalFederateException;
}
