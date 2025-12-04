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

import org.eclipse.mosaic.lib.util.ClassUtils;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Information to be processed at a simulation point is called event.
 * The event requires at least a time and a processor. The event can
 * optionally hold a priority and a resource.
 */
@Immutable
public class Event implements Comparable<Event> {
    /**
     * The minimal {@link #nice} value an {@link Event} can have.
     */
    public static final long NICE_MIN_PRIORITY = Long.MAX_VALUE;
    /**
     * The default {@link #nice} value an {@link Event} can have.
     */
    public static final long NICE_DEFAULT_PRIORITY = 0;
    /**
     * The maximal {@link #nice} value an {@link Event} can have.
     */
    public static final long NICE_MAX_PRIORITY = Long.MIN_VALUE;

    /**
     * The sequence number set by the event scheduler impl to preserve event order.
     */
    @SuppressWarnings(
            value = "JCIP_FIELD_ISNT_FINAL_IN_IMMUTABLE_CLASS",
            justification = "It's ok for now, until one finds a better way to set a sequence number once the event has been scheduled."
    )
    long seqNr = 0;

    /**
     * The simulation time to execute this event. Unit: [ns].
     */
    private final long time;

    /**
     * The niceness of this event in natural order. Low niceness means a high
     * priority. A high niceness means a low priority. If no nice value is
     * specified in the constructor {@link #NICE_DEFAULT_PRIORITY} will be applied.
     */
    private final long nice;

    /**
     * This list holds all processors, which should execute this event. The list
     * is an unmodifiable view of all processors. Try to use an ArrayList to
     * increase the performance.
     */
    @Nonnull
    private final List<EventProcessor> processors;

    /**
     * The resource to process.
     */
    @Nullable
    private final Object resource;

    /**
     * Constructor for convenience. See
     * {@link #Event(long, List, Object, long)}. No resource and no nice value
     * is given.
     *
     * @param time      The simulation time to execute this event. Unit: [ns].
     * @param processor The processor, which should execute the event.
     */
    public Event(final long time, @Nonnull final EventProcessor processor) {
        this(time, processor, null, NICE_DEFAULT_PRIORITY);
    }

    /**
     * Constructor for convenience. See
     * {@link #Event(long, List, Object, long)}. Only one processor is given.
     *
     * @param time      The simulation time to execute this event. Unit: [ns].
     * @param processor The processor, which should execute the event.
     * @param resource  The resource to process.
     */
    public Event(final long time, @Nonnull final EventProcessor processor,
                 @Nullable final Object resource) {
        this(time, Collections.singletonList(processor), resource, NICE_DEFAULT_PRIORITY);
    }

    /**
     * Constructor for convenience.
     *
     * @param time      The simulation time to execute this event. Unit: [ns].
     * @param processor The processor, which should execute the event.
     * @param resource  The resource to process.
     * @param nice      The niceness of this event in natural order. Low niceness
     *                  means a high priority. A high niceness means a low priority.
     */
    public Event(final long time, @Nonnull final EventProcessor processor,
                 @Nullable final Object resource, final long nice) {
        this(time, Collections.singletonList(processor), resource, nice);
    }

    /**
     * This constructor creates a new event.
     *
     * @param time       the time for the event. Unit: [ns].
     * @param processors the processor, which should execute the event. The list
     *                   may have no element, which is null.
     * @param resource   The resource to process.
     * @param nice       The niceness of this event in natural order. Low niceness
     *                   means a high priority. A high niceness means a low priority.
     */
    Event(final long time, @Nonnull final List<EventProcessor> processors, @Nullable final Object resource, final long nice) {

        this.time = time;

        Validate.isTrue(!processors.isEmpty(), "The processor list must contain at minimum one processor.");
        for (EventProcessor processor : processors) {
            Objects.requireNonNull(processor, "All event processors must not be null.");
        }

        this.processors = List.copyOf(processors);
        this.resource = resource;
        this.nice = nice;
    }


    /**
     * Returns the simulation time to execute this event. Unit: [ns].
     *
     * @return The simulation time to execute this event. Unit: [ns].
     */
    public long getTime() {
        return time;
    }

    /**
     * Returns the niceness of this event in natural order. Low niceness means a
     * high priority. A high niceness means a low priority.
     *
     * @return The niceness of this event in natural order. Low niceness means a high priority. A high niceness means a low priority.
     */
    public long getNice() {
        return nice;
    }

    /**
     * Returns the resource to process.
     *
     * @return The resource to process.
     */
    @Nonnull
    public List<EventProcessor> getProcessors() {
        return processors;
    }

    /**
     * Returns the resource to process.
     *
     * @return The resource to process.
     */
    @Nullable
    public Object getResource() {
        return resource;
    }

    /**
     * Returns the class name of the resource if given.
     *
     * @return The class name. Otherwise a string contains <code>null</code>.
     */
    @Nullable
    public String getResourceClassSimpleName() {
        return getResource() != null
                ? ClassUtils.createShortClassName(getResource().getClass())
                : null;
    }

    /**
     * Runs all the processors of this event.
     *
     * @return the number of processed events.
     */
    int execute() {
        int processedEvents = 0;

        for (final EventProcessor processor : getProcessors()) {
            if (processor.canProcessEvent()) {
                try {
                    processor.processEvent(this);
                    processedEvents++;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        return processedEvents;
    }

    @Override
    public int compareTo(final Event event) {
        return new CompareToBuilder()
                .append(this.time, event.time)
                .append(this.nice, event.nice)
                .append(this.seqNr, event.seqNr)
                .toComparison();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(5, 11)
                .append(time)
                .append(nice)
                .append(processors)
                .append(resource)
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

        Event rhs = (Event) obj;
        return new EqualsBuilder()
                .append(this.time, rhs.time)
                .append(this.nice, rhs.nice)
                .append(this.processors, rhs.processors)
                .append(this.resource, rhs.resource)
                .isEquals();
    }

    @Override
    public String toString() {
        return "Event{" + "time=" + time + ", nice=" + nice
                + ", processors=" + processors + ", resource=" + resource + '}';
    }

}
