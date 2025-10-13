import xml.etree.ElementTree as ET
import csv

def parse_xml_file():
    xml_file = f"./../../{scenario_name}/sumo/dispatchInfos{dispatch_algorithm}.xml"
    csv_file = f"{dispatch_algorithm}_taxi_data.csv"

    # Parse XML
    tree = ET.parse(xml_file)
    root = tree.getroot()

    # Dictionary to store taxi_id -> [passenger_counts]
    taxi_data = {}

    for dispatch in root.findall("dispatchShared"):
        taxi_id = dispatch.get("id")
        sharing_str = dispatch.get("sharingPersons", "").strip()

        # Calculate number of passengers
        num_passengers = 0
        if sharing_str:
            sharing_persons = set(sharing_str.split())
            num_passengers = len(sharing_persons) if dispatch_algorithm == "RouteExtension" else len(sharing_persons) + 1

        # Store results
        taxi_data.setdefault(taxi_id, []).append(num_passengers)

    # Compute averages
    averages = {
        taxi_id: sum(values) / len(values)
        for taxi_id, values in taxi_data.items()
    }

    # Write to CSV
    with open(csv_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["taxi_id", "average_passengers"])
        for taxi_id, avg in sorted(averages.items()):
            writer.writerow([taxi_id, round(avg, 2)])


if __name__ == "__main__":
    scenario_name = "naunhof"
    dispatch_algorithm = "RouteExtension"
    parse_xml_file()
