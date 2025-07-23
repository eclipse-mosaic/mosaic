package org.eclipse.mosaic.app.taxi.util;

import java.util.ArrayList;

public class TaxiLatestData {
	private int lastStatus;
	private final ArrayList<String> edgesToVisit;
	private Integer currentLegId;
	private final ArrayList<Integer> nextLegIds;

	public TaxiLatestData(int lastStatus, ArrayList<String> edgesToVisit, Integer currentLegId, ArrayList<Integer> nextLegIds) {
		this.lastStatus = lastStatus;
		this.edgesToVisit = edgesToVisit;
		this.currentLegId = currentLegId;
		this.nextLegIds = nextLegIds;
	}

	public int getLastStatus() {
		return lastStatus;
	}

	public void setLastStatus(int lastStatus) {
		this.lastStatus = lastStatus;
	}

	public ArrayList<String> getEdgesToVisit() {
		return edgesToVisit;
	}

	public Integer getCurrentLegId() {
		return currentLegId;
	}

	public void setCurrentLegId(Integer currentLegId) {
		this.currentLegId = currentLegId;
	}

	public ArrayList<Integer> getNextLegIds() {
		return nextLegIds;
	}
}
