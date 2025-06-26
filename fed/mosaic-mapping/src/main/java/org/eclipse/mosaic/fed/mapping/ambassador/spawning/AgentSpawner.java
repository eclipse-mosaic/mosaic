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

package org.eclipse.mosaic.fed.mapping.ambassador.spawning;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import org.eclipse.mosaic.fed.mapping.ambassador.SpawningFramework;
import org.eclipse.mosaic.fed.mapping.config.CMappingConfiguration;
import org.eclipse.mosaic.fed.mapping.config.CPrototype;
import org.eclipse.mosaic.fed.mapping.config.units.CAgent;
import org.eclipse.mosaic.interactions.mapping.AgentRegistration;
import org.eclipse.mosaic.lib.geo.GeoPoint;
import org.eclipse.mosaic.lib.math.SpeedUtils;
import org.eclipse.mosaic.lib.objects.UnitNameGenerator;
import org.eclipse.mosaic.rti.TIME;
import org.eclipse.mosaic.rti.api.IllegalValueException;
import org.eclipse.mosaic.rti.api.InternalFederateException;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for configuring Road Side Units to be added to the simulation.
 */
public class AgentSpawner extends UnitSpawner {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSpawner.class);

    public static final double DEFAULT_WALKING_SPEED = SpeedUtils.kmh2ms(5);

    private final GeoPoint origin;
    private final GeoPoint destination;

    private boolean spawningTimeRequested = false;
    private long spawningTime;
    private Double walkingSpeed;

    public AgentSpawner(CAgent agentConfiguration) {
        super(agentConfiguration.applications, agentConfiguration.name, agentConfiguration.group);
        this.spawningTime = Double.valueOf(agentConfiguration.startingTime * TIME.SECOND).longValue();
        this.origin = agentConfiguration.origin;
        this.destination = agentConfiguration.destination;
        this.walkingSpeed = agentConfiguration.walkingSpeed;
    }

    public void configure(CMappingConfiguration mappingParameterizationConfiguration) {
        if (mappingParameterizationConfiguration.start != null) {
            this.spawningTime = Double.valueOf(mappingParameterizationConfiguration.start * TIME.SECOND).longValue();
        }
    }

    /**
     * Sends out an {@link AgentRegistration} interaction as soon as the startingTime of the agent
     * has been reached. If the current time is lower than planned spawning time, the RTI is requested
     * (once) for a time advance to the provided startingTime of the agent.
     *
     * @return <code>true</code>, if the {@link AgentRegistration} was sent, <code>false</code> otherwise.
     */
    public boolean timeAdvance(SpawningFramework framework) throws InternalFederateException {
        if (framework.getTime() < spawningTime) {
            if (!spawningTimeRequested) {
                try {
                    framework.getRti().requestAdvanceTime(spawningTime);
                    spawningTimeRequested = true;
                } catch (IllegalValueException e) {
                    LOG.error("Exception while requesting spawning time for Agent.");
                    throw new InternalFederateException("Exception while sending Interaction in AgentSpawner.init()", e);
                }
            }
            return false;
        }

        final String name = UnitNameGenerator.nextAgentName();
        final AgentRegistration interaction = new AgentRegistration(
                spawningTime, name, group, origin, destination, getApplications(), defaultIfNull(walkingSpeed, DEFAULT_WALKING_SPEED)
        );
        try {
            LOG.info("Creating Agent: {}", this);
            framework.getRti().triggerInteraction(interaction);
        } catch (IllegalValueException e) {
            LOG.error("Exception while sending Interaction for Agent.");
            throw new InternalFederateException("Exception while sending Interaction in AgentSpawner.init()", e);
        }
        return true;
    }

    @Override
    public void fillInPrototype(CPrototype prototypeConfiguration) {
        super.fillInPrototype(prototypeConfiguration);

        if (prototypeConfiguration != null) {
            walkingSpeed = defaultIfNull(walkingSpeed, prototypeConfiguration.maxSpeed);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("startingTime", spawningTime)
                .append("origin", origin)
                .append("destination", destination)
                .append("applications", applications)
                .build();
    }
}
