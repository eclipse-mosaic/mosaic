import xml.etree.ElementTree as ET
import csv

def parse_xml_file():
    # Input XML and output CSV filenames
    xml_file = f"./../../{scenario_name}/sumo/tripInfos{dispatch_algorithm}.xml"
    csv_file = f"{dispatch_algorithm}_person_rides.csv"

    # Parse the XML file
    tree = ET.parse(xml_file)
    root = tree.getroot()

    not_started_orders = 0
    started_but_not_finished_orders = 0

    # Open CSV for writing
    with open(csv_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["person_id", "waiting_time", "vehicle_id", "arrival", "not_started", "started_but_not_finished"])  # Header

        # Iterate through each personinfo node
        for person in root.findall("personinfo"):
            person_id = person.get("id")
            waiting_time = person.get("waitingTime")

            ride = person.find("ride")

            # Only if a ride node exists
            if ride is not None:
                vehicle_id = ride.get("vehicle")

                not_started = False
                if ride.get("depart") == "-1":
                    not_started = True
                    not_started_orders += 1

                started_but_not_finished = False
                if not not_started and ride.get("arrival") == "-1":
                    started_but_not_finished = True
                    started_but_not_finished_orders += 1

                arrival_time = ride.get("arrival")
                writer.writerow([person_id, waiting_time, vehicle_id, arrival_time, not_started, started_but_not_finished])

    taxi_csv_file = f"{dispatch_algorithm}_taxi_rides.csv"

    with open(taxi_csv_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(['taxi_id', 'operation_time_percentage'])

        for trip_info in root.findall("tripinfo"):
            taxi_id = trip_info.get("id")
            waiting_time_taxi = trip_info.get("waitingTime")
            stop_time = trip_info.get("stopTime")
            taxi_device = trip_info.find("taxi")

            if taxi_device is None:
                continue

            operation_time_percentage = round((4200 - float(waiting_time_taxi) - float(stop_time)) * 100 / 4200, 2)
            writer.writerow([taxi_id, operation_time_percentage])


    xml_file = f"./../../{scenario_name}/sumo/dispatchInfos{dispatch_algorithm}.xml"

    # Parse the XML file
    tree = ET.parse(xml_file)
    root = tree.getroot()

    # Find all dispatchShared elements
    dispatch_nodes = root.findall("dispatchShared")
    print(f"Number of dispatchShared nodes: {len(dispatch_nodes)}")

    number_of_shared_orders = 0
    for dispatch_node in dispatch_nodes:
         number_of_shared_orders += len(set(dispatch_node.get("sharingPersons").split()))

    number_of_passengers_greedy_shared = len(dispatch_nodes) * 2
    mult = number_of_passengers_greedy_shared if dispatch_algorithm == "GreedyShared" else number_of_shared_orders
    percentage_shared_orders = mult * 100 / (500 - not_started_orders)
    print(f"Percentage of shared orders: {percentage_shared_orders}")
    print(f"Number of not started order: {not_started_orders}")
    print(f"Number of started but not finished orders: {started_but_not_finished_orders}")

if __name__ == "__main__":
    scenario_name = "naunhof"
    dispatch_algorithm = "RouteExtension"
    parse_xml_file()
