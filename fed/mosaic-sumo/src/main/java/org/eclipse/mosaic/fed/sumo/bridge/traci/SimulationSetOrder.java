package org.eclipse.mosaic.fed.sumo.bridge.traci;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.fed.sumo.bridge.TraciVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.Status;
import org.eclipse.mosaic.fed.sumo.bridge.traci.constants.CommandSimulationControl;
import org.eclipse.mosaic.rti.api.InternalFederateException;

public class SimulationSetOrder extends AbstractTraciCommand<Void> implements org.eclipse.mosaic.fed.sumo.bridge.api.SimulationSetOrder {


    @SuppressWarnings("WeakerAccess")
    public SimulationSetOrder() {
        super(TraciVersion.LOWEST);

        write()
                .command(CommandSimulationControl.COMMAND_SET_ORDER)
                .writeIntParamWithType();
    }

    @Override
    public void execute(Bridge bridge, int order) throws CommandException, InternalFederateException {
        super.execute(bridge, order);
    }

    @Override
    protected Void constructResult(Status status, Object... objects) {
        return null;
    }
}
