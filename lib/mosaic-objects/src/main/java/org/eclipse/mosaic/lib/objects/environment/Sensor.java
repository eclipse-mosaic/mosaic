/*
 * Copyright (c) 2025 Fraunhofer FOKUS and others. All rights reserved.
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

package org.eclipse.mosaic.lib.objects.environment;

import org.eclipse.mosaic.lib.enums.EnvironmentEventCause;
import org.eclipse.mosaic.lib.enums.TractionHazard;
import org.eclipse.mosaic.lib.enums.VisibilityHazard;
import org.eclipse.mosaic.lib.util.ConversionUtils;

import java.util.function.Function;

/**
 * An environment sensor used to return values or objects which have been configured for specific areas. Currently, solely used by
 * mosaic-application in combination with mosaic-environment. In mosaic-environment, certain events are configured in areas.
 * The type of the event corresponds to one of the provided sensors (or custom implementations of {@link Sensor}), identified
 * by the name of the sensor. The value that is applied is taken from the same configuration in mosaic-environment, and can then
 * be "sensed" by the provided {@link Sensor} in the application of the simulation unit.
 */
public class Sensor<T> {

    /**
     * A sensor that senses a specific "TEMPERATURE" in an area, with user-defined temperature (double).
     */
    public final static Sensor<Double> TEMPERATURE = new Sensor<>("TEMPERATURE", ConversionUtils::toDouble);

    /**
     * A sensor that senses properties of wind (direction and wind speed), not including airflow experienced from driving.
     */
    public final static Sensor<Wind> WIND = new Sensor<>("WIND", Wind::fromObject);

    /**
     * A sensor that senses a hazard resulting in reduced visibility, caused by rain, snow, fog, or dust.
     */
    public final static Sensor<VisibilityHazard> VISIBILITY_HAZARD = new Sensor<>("VISIBILITY_HAZARD", VisibilityHazard::fromObject);

    /**
     * A sensor that senses a hazard regarding vehicle traction, caused by icy roads, aqua planning, snow or mud.
     */
    public final static Sensor<TractionHazard> TRACTION_HAZARD = new Sensor<>("TRACTION_HAZARD", TractionHazard::fromObject);

    /**
     * A sensor that senses the condition of the road-surface (e.g., IRI).
     */
    public final static Sensor<Double> ROAD_SURFACE_CONDITION = new Sensor<>("ROAD_SURFACE_CONDITION", ConversionUtils::toDouble);

    /**
     * A sensor that senses hazardous events of a specific {@link EnvironmentEventCause}.
     */
    public final static Sensor<EnvironmentEventCause> EVENT = new Sensor<>("EVENT", EnvironmentEventCause::fromObject);

    private final String name;
    private final Function<Object, T> translator;

    /**
     * Creates a new custom {@link Sensor}. This is usually only required, if the pre-defined sensors are not sufficient, but
     * can be used to extend the types of events supported by the Environment Sensor.
     *
     * @param name       the name of this sensor, must correspond to the event types configured in mosaic-environment.
     * @param translator a function to translate any object to the required type T
     */
    public Sensor(String name, Function<Object, T> translator) {
        this.name = name;
        this.translator = translator;
    }

    /**
     * Returns the name of this sensor.
     */
    public final String getName() {
        return name;
    }

    /**
     * Translates an object (e.g., which configured in the environment_config.json) to the actual value and correct type of
     * this {@link Sensor}. The input object is most likely to be a primitive type, such as {@link Boolean}, {@link Integer},
     * {@link Long}, {@link Double}, or {@link String}.
     *
     * @param value the input value of unknown type
     * @return the input value translated to the parametrized type T of this {@link Sensor}
     */
    public T translate(Object value) {
        return translator.apply(value);
    }
}

