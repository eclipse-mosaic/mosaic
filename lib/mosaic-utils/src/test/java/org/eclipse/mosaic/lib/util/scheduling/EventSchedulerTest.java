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

package org.eclipse.mosaic.lib.util.scheduling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RunWith(Parameterized.class)
public class EventSchedulerTest {

    private boolean canProcessEvents = true;

    /**
     * A list to add all processed events.
     */
    final List<Event> processedEvents = new CopyOnWriteArrayList<>();

    /**
     * An event processor to add processed events to the list.
     */
    final EventProcessor processor = new EventProcessor() {
        @Override
        public void processEvent(final Event event) {
            processedEvents.add(event);
        }

        @Override
        public boolean canProcessEvent() {
            return canProcessEvents;
        }
    };

    private final int eventSchedulerThreads;

    /**
     * The event scheduler reference.
     */
    private EventScheduler eventScheduler;

    public EventSchedulerTest(int eventSchedulerThreads) {
        this.eventSchedulerThreads = eventSchedulerThreads;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {1}, {2}
        });
    }

    @Before
    public void setUp() {
        // create a new event scheduler
        if (eventSchedulerThreads == 1) {
            eventScheduler = new DefaultEventScheduler();
        } else {
            eventScheduler = new MultiThreadedEventScheduler(eventSchedulerThreads);
        }
        // first of all create some events and add them to the scheduler
        eventScheduler.addEvent(new Event(0, processor, null));
        eventScheduler.addEvent(1, processor);
        eventScheduler.newEvent(2, processor).withResource(null).withNice(0).schedule();
    }

    @After
    public void tearDown() {
        processedEvents.clear();
    }

    @Test
    public void testEventScheduler() {
        assertEquals(Long.MIN_VALUE, eventScheduler.getScheduledTime());

        // schedule first event
        eventScheduler.scheduleEvents(0);
        assertEquals(0, eventScheduler.getScheduledTime());
        assertEquals(0, processedEvents.get(0).getTime());
        assertEquals(1, processedEvents.size());

        // schedule second event
        eventScheduler.scheduleEvents(1);
        assertEquals(1, eventScheduler.getScheduledTime());
        assertEquals(1, processedEvents.get(1).getTime());
        assertEquals(2, processedEvents.size());

        // schedule third event
        eventScheduler.scheduleEvents(2);
        assertEquals(2, eventScheduler.getScheduledTime());
        assertEquals(2, processedEvents.get(2).getTime());
        assertEquals(3, processedEvents.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEventSchedulerEventLiesInThePast() {
        // schedule all events until the time 2
        eventScheduler.scheduleEvents(0);
        eventScheduler.scheduleEvents(1);
        eventScheduler.scheduleEvents(2);
        // all three events should be scheduled
        assertEquals(3, processedEvents.size());
        // try to add an event which lies in the past
        eventScheduler.addEvent(0, processor);
    }

    @Test(expected = RuntimeException.class)
    public void testMissingSchedule() {
        // schedule the time 1 (0 was ignored)
        eventScheduler.isEmpty();
        eventScheduler.scheduleEvents(1);
    }

    @Test
    public void testIsNotEmpty() {
        assertFalse(eventScheduler.isEmpty());
    }

    @Test
    public void testGetAllEvents() {
        // all events in the scheduler
        assertEquals(3, eventScheduler.getAllEvents().size());
    }

    @Test
    public void testScheduleAllEvents() {
        eventScheduler.scheduleEvents(0);
        eventScheduler.scheduleEvents(1);
        eventScheduler.scheduleEvents(2);
        assertEquals(2, eventScheduler.getScheduledTime());
    }

    @Test
    public void testScheduleMultipleTime() {
        eventScheduler.scheduleEvents(0);
        eventScheduler.scheduleEvents(1);
        eventScheduler.scheduleEvents(2);
        assertEquals(2, eventScheduler.getScheduledTime());
        eventScheduler.addEvent(2, processor);
        eventScheduler.scheduleEvents(2);
        assertEquals(2, eventScheduler.getScheduledTime());
    }

    @Test(expected = IllegalStateException.class)
    public void testNoNextEventTime() {
        eventScheduler.scheduleEvents(0);
        eventScheduler.scheduleEvents(1);
        eventScheduler.scheduleEvents(2);
        assertTrue(eventScheduler.isEmpty());
        eventScheduler.getNextEventTime();
    }

    @Test
    public void testScheduleEvents_processorCanNotProcess() {
        canProcessEvents = false;

        eventScheduler.scheduleEvents(0);
        eventScheduler.scheduleEvents(1);
        assertTrue(processedEvents.isEmpty());

        //
        canProcessEvents = true;

        eventScheduler.scheduleEvents(2);
        assertFalse(processedEvents.isEmpty());
    }

    @Test
    public void testScheduleEvents_sameTime_respectNiceValueAndOrder() {
        //SETUP
        eventScheduler.addEvent(new Event(0, processor, "a", 20));
        eventScheduler.addEvent(new Event(0, processor, "b", 10));
        eventScheduler.addEvent(new Event(0, processor, "c", 10));
        eventScheduler.addEvent(new Event(0, processor, "d", 1));
        eventScheduler.addEvent(new Event(0, processor, "e", 10));

        //RUN
        int totalProcessedEvents = eventScheduler.scheduleEvents(0);

        //ASSERT
        assertEquals(6, totalProcessedEvents);

        assertEquals(0, processedEvents.get(0).getNice());
        assertEquals(1, processedEvents.get(1).getNice());
        assertEquals(10, processedEvents.get(2).getNice());
        assertEquals(10, processedEvents.get(3).getNice());
        assertEquals(10, processedEvents.get(4).getNice());
        assertEquals(20, processedEvents.get(5).getNice());

        assertNull(processedEvents.get(0).getResource());
        assertEquals("d", processedEvents.get(1).getResource());
        ArrayList<String> nice10Values = new ArrayList<>(Arrays.asList("b", "c", "e"));
        assertTrue(nice10Values.contains(processedEvents.get(2).getResource()));
        assertTrue(nice10Values.contains(processedEvents.get(3).getResource()));
        assertTrue(nice10Values.contains(processedEvents.get(4).getResource()));
        assertEquals("a", processedEvents.get(5).getResource());
    }

    /**
     * A test event producer that schedules events in the future.
     * Stores all the processed events into a list.
     */
    private class EventProducer implements EventProcessor {

        /**
         * A list to add all processed events.
         */
        private final List<Event> processedEvents = new ArrayList<>();
        private static final int totalEvents = 400;
        private static final int timeInterval = 4;
        private int eventCount = 0;

        private final EventScheduler eventScheduler;

        public EventProducer(EventScheduler eventScheduler) {
            this.eventScheduler = eventScheduler;
        }

        public List<Event> getProcessedEvents() {
            return processedEvents;
        }

        @Override
        public void processEvent(final Event event) throws Exception {
            processedEvents.add(event);
            if (eventCount <= totalEvents) {
                long scheduledTIme = (eventScheduler.getScheduledTime() > 0) ? eventScheduler.getScheduledTime() : 0;
                eventScheduler.addEvent(new Event(scheduledTIme + timeInterval, this));
            }
            eventCount++;
        }

        @Override
        public boolean canProcessEvent() {
            return true;
        }
    }

    @Test
    public void testSchedulerWithProducerEvents() {
        List<EventProducer> processors = new ArrayList<>();

        final int processorsCount = 4;
        for (int i = 0; i < processorsCount; i++) {
            EventProducer processor = new EventProducer(eventScheduler);
            processors.add(processor);
            eventScheduler.addEvent(new Event(0, processor));
        }

        /*
         * Process setup events
         */
        int totalProcessedEvents = eventScheduler.scheduleEvents(0);
        assertEquals(1 + processorsCount, totalProcessedEvents);

        totalProcessedEvents = eventScheduler.scheduleEvents(1);
        assertEquals(1, totalProcessedEvents);
        totalProcessedEvents = eventScheduler.scheduleEvents(2);
        assertEquals(1, totalProcessedEvents);

        /*
         * Process remaining events
         */
        for (int i = 3; i < EventProducer.timeInterval * EventProducer.totalEvents; i++) {
            totalProcessedEvents = eventScheduler.scheduleEvents(i);

            if (i % EventProducer.timeInterval == 0) {
                /*
                 * Test if processor increased its eventCount by one in each iteration
                 */
                assertEquals(processors.size(), totalProcessedEvents);
                for (EventProducer eventProcessor : processors) {
                    assertEquals((i / EventProducer.timeInterval) + 1, eventProcessor.getProcessedEvents().size());
                }
            } else {
                /*
                 * Test that none events where processed in this time
                 */
                assertEquals(0, totalProcessedEvents);
            }
        }

        /*
         * Test total number of events processed by all processors
         */
        for (EventProducer eventProcessor : processors) {
            assertEquals(eventProcessor.getProcessedEvents().size(), EventProducer.totalEvents);
        }
    }
}
