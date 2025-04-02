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

import org.eclipse.mosaic.fed.mapping.ambassador.weighting.Weighted;
import org.eclipse.mosaic.fed.mapping.config.CParameterDeviations;
import org.eclipse.mosaic.fed.mapping.config.CPrototype;
import org.eclipse.mosaic.lib.enums.LaneChangeMode;
import org.eclipse.mosaic.lib.enums.SpeedMode;
import org.eclipse.mosaic.lib.enums.VehicleClass;
import org.eclipse.mosaic.lib.math.MathUtils;
import org.eclipse.mosaic.lib.math.RandomNumberGenerator;
import org.eclipse.mosaic.lib.objects.vehicle.VehicleType;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Objects;

/**
 * This class defines how a vehicle type can be spawned using
 * information provided by the json configuration.
 */
public class VehicleTypeSpawner extends UnitSpawner implements Weighted {
    /**
     * The weight of the Vehicle (used for spawning selection).
     */
    private Double weight;

    /**
     * Length of the vehicle in meter.
     */
    private Double length;

    /**
     * Width of the vehicle in meter.
     */
    private Double width;

    /**
     * Height of the vehicle in meter.
     */
    private Double height;

    /**
     * Distance in meters between front bumper of a vehicle
     * and the back bumper of its leader in a traffic jam.
     */
    private Double minGap;

    /**
     * Maximal speed in m/s.
     */
    private Double maxSpeed;

    /**
     * Class of the vehicle.
     */
    private VehicleClass vehicleClass;

    /**
     * Acceleration m/s^2.
     */
    private Double accel;

    /**
     * Deceleration in m/s^2.
     */
    private Double decel;

    /**
     * Emergency deceleration. The maximum deceleration a vehicle can take to avoid an accident. Unit: [m/s^2].
     */
    private Double emergencyDecel;

    /**
     * Driver imperfection.
     */
    private Double sigma;

    /**
     * Driver reaction time in seconds.
     */
    private Double tau;

    /**
     * The speed factor to be applied. With a value over 1 a vehicle would exceed the speed limit of an edge
     */
    private Double speedFactor;

    /**
     * The color of the vehicle for visualization purposes.
     */
    private String color;

    /**
     * The laneChangeMode of the vehicle. Accepted values are String representations of {@link LaneChangeMode}.
     */
    private LaneChangeMode laneChangeMode;

    /**
     * The speedMode of the vehicle.
     */
    private SpeedMode speedMode;

    /**
     * Parameters to deviate for each individual vehicle based in this prototype.
     */
    private CParameterDeviations deviations;

    /**
     * Constructor for {@link VehicleTypeSpawner}.
     *
     * @param prototypeConfiguration the {@link CPrototype} to infer parameters from
     */
    public VehicleTypeSpawner(CPrototype prototypeConfiguration) {
        super(prototypeConfiguration.applications, prototypeConfiguration.name, prototypeConfiguration.group);
        Objects.requireNonNull(prototypeConfiguration);

        this.weight = prototypeConfiguration.weight;

        this.length = prototypeConfiguration.length;
        this.width = prototypeConfiguration.width;
        this.height = prototypeConfiguration.height;
        this.minGap = prototypeConfiguration.minGap;
        this.maxSpeed = prototypeConfiguration.maxSpeed;
        this.vehicleClass = prototypeConfiguration.vehicleClass;
        this.accel = prototypeConfiguration.accel;
        this.decel = prototypeConfiguration.decel;
        this.emergencyDecel = prototypeConfiguration.emergencyDecel;
        this.sigma = prototypeConfiguration.sigma;
        this.tau = prototypeConfiguration.tau;
        this.speedFactor = prototypeConfiguration.speedFactor;
        this.color = prototypeConfiguration.color;
        this.laneChangeMode = prototypeConfiguration.laneChangeMode;
        this.speedMode = prototypeConfiguration.speedMode;

        this.deviations = prototypeConfiguration.deviations;
    }

    public VehicleType convertTypeAndVaryParameters(RandomNumberGenerator random) {
        if (deviations == null) {
            return convertType();
        }
        return new VehicleType(prototypeName,
                deviateWithBounds(random, length, deviations.length),
                deviateWithBounds(random, width, deviations.width),
                deviateWithBounds(random, height, deviations.height),
                deviateWithBounds(random, minGap, deviations.minGap),
                deviateWithBounds(random, maxSpeed, deviations.maxSpeed),
                vehicleClass,
                deviateWithBounds(random, accel, deviations.accel),
                deviateWithBounds(random, decel, deviations.decel),
                emergencyDecel,
                sigma,
                deviateWithBounds(random, tau, deviations.tau),
                deviateWithBounds(random, speedFactor, deviations.speedFactor),
                color, laneChangeMode, speedMode
        );
    }

    Double deviateWithBounds(RandomNumberGenerator random, Double mean, Double deviation) {
        if (mean != null && deviation != null && Math.abs(deviation) > 0.0001) {
            double randomValue = random.nextGaussian(mean, deviation);

            return MathUtils.clamp(randomValue,  mean - 2 * deviation,  mean + 2 * deviation);
        }
        return mean;
    }

    public void setPrototypeName(String prototypeName) {
        this.prototypeName = prototypeName;
    }

    public double getWeight() {
        return weight;
    }

    public VehicleClass getVehicleClass() {
        return vehicleClass;
    }

    /**
     * Creates a {@link VehicleType} from the internal class fields.
     *
     * @return the generated {@link VehicleType}
     */
    public VehicleType convertType() {
        return new VehicleType(
                prototypeName,
                length,
                width,
                height,
                minGap,
                maxSpeed,
                vehicleClass,
                accel, decel,
                emergencyDecel,
                sigma,
                tau,
                speedFactor,
                color,
                laneChangeMode,
                speedMode
        );
    }

    /**
     * This method fills in all properties, that weren't set in
     * the units' configuration using definitions from the prototype.
     * This method overrides {@link UnitSpawner#fillInPrototype(CPrototype)},
     * since it additionally sets all the vehicle parameters.
     *
     * @param prototypeConfiguration the {@link CPrototype} to inherit parameters from
     */
    @Override
    public void fillInPrototype(CPrototype prototypeConfiguration) {
        // no valid prototype configuration
        if (prototypeConfiguration == null) {
            return;
        }

        super.fillInPrototype(prototypeConfiguration);

        // inherit spawning weight
        weight = defaultIfNull(weight, prototypeConfiguration.weight);

        // inherit additional vehicle parameters.
        vehicleClass = defaultIfNull(vehicleClass, prototypeConfiguration.vehicleClass);
        color = defaultIfNull(color, prototypeConfiguration.color);
        laneChangeMode = defaultIfNull(laneChangeMode, prototypeConfiguration.laneChangeMode);
        speedMode = defaultIfNull(speedMode, prototypeConfiguration.speedMode);

        accel = defaultIfNull(accel, prototypeConfiguration.accel);
        decel = defaultIfNull(decel, prototypeConfiguration.decel);
        emergencyDecel = defaultIfNull(emergencyDecel, prototypeConfiguration.emergencyDecel);
        length = defaultIfNull(length, prototypeConfiguration.length);
        width = defaultIfNull(width, prototypeConfiguration.width);
        height = defaultIfNull(height, prototypeConfiguration.height);
        maxSpeed = defaultIfNull(maxSpeed, prototypeConfiguration.maxSpeed);
        minGap = defaultIfNull(minGap, prototypeConfiguration.minGap);

        sigma = defaultIfNull(sigma, prototypeConfiguration.sigma);
        tau = defaultIfNull(tau, prototypeConfiguration.tau);
        speedFactor = defaultIfNull(speedFactor, prototypeConfiguration.speedFactor);

        deviations = defaultIfNull(deviations, prototypeConfiguration.deviations);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append("name", prototypeName)
                .append("apps", applications)
                .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(1, 31)
                .append(prototypeName)
                .append(applications)
                .append(group)
                .append(weight)
                .append(accel)
                .append(decel)
                .append(emergencyDecel)
                .append(length)
                .append(width)
                .append(height)
                .append(maxSpeed)
                .append(minGap)
                .append(sigma)
                .append(tau)
                .append(speedFactor)
                .append(vehicleClass)
                .append(color)
                .append(laneChangeMode)
                .append(speedMode)
                .append(deviations)
                .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        VehicleTypeSpawner other = (VehicleTypeSpawner) obj;
        return new EqualsBuilder()
                .append(prototypeName, other.prototypeName)
                .append(applications, other.applications)
                .append(group, other.group)
                .append(weight, other.weight)
                .append(accel, other.accel)
                .append(decel, other.decel)
                .append(emergencyDecel, other.emergencyDecel)
                .append(length, other.length)
                .append(width, other.width)
                .append(height, other.height)
                .append(maxSpeed, other.maxSpeed)
                .append(minGap, other.minGap)
                .append(sigma, other.sigma)
                .append(tau, other.tau)
                .append(speedFactor, other.speedFactor)
                .append(vehicleClass, other.vehicleClass)
                .append(color, other.color)
                .append(laneChangeMode, other.laneChangeMode)
                .append(speedMode, other.speedMode)
                .append(deviations, other.deviations)
                .isEquals();
    }
}
