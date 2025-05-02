package org.eclipse.mosaic.lib.objects.taxi;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class TaxiVehicleData implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	private final String state;
	private final String numberOfCustomersServed;
	private final String totalOccupiedDistanceInMeters;
	private final String totalOccupiedTimeInSeconds;
	private final List<String> customersToPickUpOrOnBoard;

	private TaxiVehicleData(String state, String numberOfCustomersServed, String totalOccupiedDistanceInMeters,
		String totalOccupiedTimeInSeconds, List<String> customersToPickUpOrOnBoard) {
		this.state = state;
		this.numberOfCustomersServed = numberOfCustomersServed;
		this.totalOccupiedDistanceInMeters = totalOccupiedDistanceInMeters;
		this.totalOccupiedTimeInSeconds = totalOccupiedTimeInSeconds;
		this.customersToPickUpOrOnBoard = customersToPickUpOrOnBoard;
	}

	public String getState() {
		return state;
	}

	public String getNumberOfCustomersServed() {
		return numberOfCustomersServed;
	}

	public String getTotalOccupiedDistanceInMeters() {
		return totalOccupiedDistanceInMeters;
	}

	public String getTotalOccupiedTimeInSeconds() {
		return totalOccupiedTimeInSeconds;
	}

	public List<String> getCustomersToPickUpOrOnBoard() {
		return customersToPickUpOrOnBoard;
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

		TaxiVehicleData other = (TaxiVehicleData) obj;
		return new EqualsBuilder()
			.appendSuper(super.equals(obj))
			.append(this.state, other.state)
			.append(this.numberOfCustomersServed, other.numberOfCustomersServed)
			.append(this.totalOccupiedDistanceInMeters, other.totalOccupiedDistanceInMeters)
			.append(this.totalOccupiedTimeInSeconds, other.totalOccupiedTimeInSeconds)
			.append(this.customersToPickUpOrOnBoard, other.customersToPickUpOrOnBoard)
			.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(3, 89)
			.appendSuper(super.hashCode())
			.append(state)
			.append(numberOfCustomersServed)
			.append(totalOccupiedDistanceInMeters)
			.append(totalOccupiedTimeInSeconds)
			.append(customersToPickUpOrOnBoard)
			.toHashCode();
	}

	public static class Builder {
		private String state;
		private String customersServed;
		private String totalOccupiedDistanceInMeters;
		private String totalOccupiedTimeInSeconds;
		private List<String> customersToPickUpOrOnBoard;

		public Builder withState(String state) {
			this.state = state;
			return this;
		}

		public Builder withCustomersServed(String customersServed) {
			this.customersServed = customersServed;
			return this;
		}

		public Builder withTotalOccupiedDistanceInMeters(String totalOccupiedDistanceInMeters) {
			this.totalOccupiedDistanceInMeters = totalOccupiedDistanceInMeters;
			return this;
		}

		public Builder withTotalOccupiedTimeInSeconds(String totalOccupiedTimeInSeconds) {
			this.totalOccupiedTimeInSeconds = totalOccupiedTimeInSeconds;
			return this;
		}

		public Builder withCustomersToPickUpOrOnBoard(String customersToPickUpOrOnBoard) {
			this.customersToPickUpOrOnBoard = Arrays.stream(customersToPickUpOrOnBoard.split(",")).toList();
			return this;
		}

		public TaxiVehicleData build() {
			return new TaxiVehicleData(state, customersServed, totalOccupiedDistanceInMeters,
				totalOccupiedTimeInSeconds, customersToPickUpOrOnBoard);
		}
	}
}
