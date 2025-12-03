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

package org.eclipse.mosaic.rti.api;

public interface PreemptableFederateAmbassador extends FederateAmbassador {

    /**
     * Same as advanceTime (see {@link FederateAmbassador}) but with the ability to signal preemption via the return value.
     * @return did complete execution successful (without preemption)
     */
    boolean advanceTimePreemptable(long time) throws InternalFederateException;

    /**
     * Return {@code true}, if preemptive execution is enabled for this ambassador.
     */
    boolean isPreemptiveExecutionEnabled();
}
