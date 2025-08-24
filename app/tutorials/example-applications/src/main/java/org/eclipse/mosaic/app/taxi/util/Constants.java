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

package org.eclipse.mosaic.app.taxi.util;

public class Constants {
	// TAXI STATUSES
	public static final int DISPATCHER_ASSIGNED_TAXI_STATUS = 0;
	public static final int DISPATCHER_FREE_TAXI_STATUS = 1;
	// ORDER STATUSES
	public static final int DISPATCHER_RECEIVED_ORDER_STATUS = 0;
	public static final int DISPATCHER_ASSIGNED_ORDER_STATUS = 1;
	public static final int DISPATCHER_ACCEPTED_ORDER_STATUS = 2;
	public static final int DISPATCHER_PICKEDUP_ORDER_STATUS = 7;
	public static final int DISPATCHER_COMPLETED_ORDER_STATUS = 8;
	// ROUTE AND LEG STATUSES
	public static final int DISPATCHER_ASSIGNED_ROUTE_LEG_STATUS = 1;
	public static final int DISPATCHER_STARTED_ROUTE_LEG_STATUS = 5;
	public static final int DISPATCHER_COMPLETED_ROUTE_LEG_STATUS = 6;
}
