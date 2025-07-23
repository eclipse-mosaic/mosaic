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
