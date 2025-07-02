package org.eclipse.mosaic.fed.sumo.bridge.traci;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.eclipse.mosaic.fed.sumo.bridge.Bridge;
import org.eclipse.mosaic.fed.sumo.bridge.SumoVersion;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.AbstractSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.PersonSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.bridge.api.complex.VehicleSubscriptionResult;
import org.eclipse.mosaic.fed.sumo.junit.SumoRunner;
import org.eclipse.mosaic.rti.TIME;

import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.List;

@RunWith(SumoRunner.class)
public class PersonSubscribeTest extends AbstractTraciCommandTest {

    @Test
    public void execute_PersonAlreadyDeparted() throws Exception {
        // PRE-ASSERT
        List<AbstractSubscriptionResult> subscriptions = simulateStep.execute(traci.getTraciConnection(), 6 * TIME.SECOND);
        assertTrue(subscriptions.isEmpty());

        // RUN
        new PersonSubscribe().execute(traci.getTraciConnection(), "p_0", 0L, 10 * TIME.SECOND);

        // ASSERT
        subscriptions = simulateStep.execute(traci.getTraciConnection(), 10 * TIME.SECOND);
        assertEquals(1, subscriptions.size());
        assertTrue(((PersonSubscriptionResult) Iterables.getOnlyElement(subscriptions)).speed > 4.0 / 3.6);

        subscriptions = simulateStep.execute(traci.getTraciConnection(), 11 * TIME.SECOND);
        assertTrue(subscriptions.isEmpty());

    }

    @Test
    public void execute_personNotYetDeparted() throws Exception {
        // RUN
        new PersonSubscribe().execute(traci.getTraciConnection(), "p_1", 0L, 10 * TIME.SECOND);

        // ASSERT
        List<AbstractSubscriptionResult> subscriptions = simulateStep.execute(traci.getTraciConnection(), 1 * TIME.SECOND);
        assertEquals(1, subscriptions.size());
        assertNull(((PersonSubscriptionResult) Iterables.getOnlyElement(subscriptions)).position);
    }

}
