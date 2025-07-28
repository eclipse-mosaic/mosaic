package org.eclipse.mosaic.app.taxi.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
public class TaxiLatestData {
	private int lastStatus;
	private final ArrayList<String> edgesToVisit;
	private Integer currentLegId;
	private final ArrayList<Integer> nextLegIds;
}
