package org.eclipse.mosaic.fed.sumo.bridge.libsumo;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.CommandException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.eclipse.sumo.libsumo.Person;

public class PersonGetTypeId implements org.eclipse.mosaic.fed.sumo.bridge.api.PersonGetTypeId {
    @Override
    public String execute(Bridge bridge, String personId) throws CommandException, InternalFederateException {
        return Person.getTypeID(Bridge.PERSON_ID_TRANSFORMER.fromExternalId(personId));
    }
}
