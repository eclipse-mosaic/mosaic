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

package org.eclipse.mosaic.lib.util.cli;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare an option for the command line.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CliOption {

    /**
     * The short option name.
     */
    String shortOption() default "";

    /**
     * The long option name.
     */
    String longOption();

    /**
     * The description of the option used for help messages.
     */
    String description();

    /**
     * The name of the value for this option. Usually, all non-boolean options need such valueHint.
     */
    String valueHint() default "";

    /**
     * The default value set at the annotated field, if an valueHint is specified, which is optional.
     */
    String defaultValue() default "";

    /**
     * The group to which this parameter option belongs to. Within a group, only one option must be used at most.
     */
    String group() default "";

    /**
     * Defines, if this parameter (or group, if set) is a required parameter.
     */
    boolean isRequired() default false;
}
