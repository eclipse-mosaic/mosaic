import xml.etree.ElementTree as ET
import random, sys, os
import xml.dom.minidom as minidom

def generate_random_person_trips(
    bus_add_file,
    output_file,
    num_trips=100,
    fixed_stop_id=None,
    fixed_stop_percentage=0.3,
    depart_time_range=(0, 3600)
):
    """
    This script generates random SUMO person trips between bus stops.

    Parameters
    ----------
    bus_add_file : str
        Path to the SUMO .add.xml containing <busStop> elements.
    output_file : str
        Path to write the generated trips XML.
    num_trips : int
        Total number of person trips to generate.
    fixed_stop_id : str
        ID of the bus stop where a percentage of trips should end.
    fixed_stop_percentage : float
        Fraction [0..1] of trips that must end at the fixed_stop_id.
    depart_time_range : tuple(int, int)
        Min and max departure time (seconds).
    """

    # Parse bus stops from SUMO .add.xml file
    tree = ET.parse(bus_add_file)
    root = tree.getroot()

    stops = []
    for bs in root.findall("busStop"):
        stops.append({
            "id": bs.get("id"),
            "lane": bs.get("lane")
        })

    if not stops:
        raise ValueError("No <busStop> elements found in the provided file.")

    # Map stop IDs to lanes
    stop_id_to_lane = {s["id"]: s["lane"] for s in stops}

    # Check fixed stop validity
    if fixed_stop_id and fixed_stop_id not in stop_id_to_lane:
        raise ValueError(f"Fixed stop '{fixed_stop_id}' not found in {bus_add_file}")

    # Determine how many trips must end at the fixed stop
    fixed_count = int(num_trips * fixed_stop_percentage) if fixed_stop_id else 0
    random_count = num_trips - fixed_count

    # Create root element for output
    routes = ET.Element("routes")

    person_index = 1

    # Generate trips ending at the fixed stop
    for _ in range(fixed_count):
        from_stop = random.choice([s for s in stops if s["id"] != fixed_stop_id])
        to_stop = {"id": fixed_stop_id, "lane": stop_id_to_lane[fixed_stop_id]}

        # Random depart time within range
        depart_time = random.randint(*depart_time_range)

        # Strip last two characters of lane IDs
        from_lane = from_stop["lane"][:-2]
        to_lane = to_stop["lane"][:-2]

        person = ET.SubElement(routes, "person", {
            "id": f"p{person_index}",
            "depart": str(depart_time)
        })
        ET.SubElement(person, "ride", {
            "from": from_lane,
            "to": to_lane,
            "lines": "taxi"
        })
        person_index += 1

    # Generate the remaining random trips
    for _ in range(random_count):
        from_stop = random.choice(stops)
        possible_stops = [s for s in stops if s["id"] != from_stop["id"]]
        to_stop = random.choice(possible_stops)
        depart_time = random.randint(*depart_time_range)
        from_lane = from_stop["lane"][:-2]
        to_lane = to_stop["lane"][:-2]
        person = ET.SubElement(routes, "person", {
            "id": f"p{person_index}",
            "depart": str(depart_time)
        })
        ET.SubElement(person, "ride", {
            "from": from_lane,
            "to": to_lane,
            "lines": "taxi"
        })
        person_index += 1

    # Convert to string and pretty print
    xml_str = ET.tostring(routes, encoding="utf-8")
    parsed = minidom.parseString(xml_str)
    pretty_xml_str = parsed.toprettyxml(indent="  ")

    # Write to file
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(pretty_xml_str)

    print(f"Generated {num_trips} trips in {output_file} "
          f"(with exactly {fixed_count} ending at {fixed_stop_id})")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        generate_random_person_trips(
            bus_add_file="../{0}/sumo/{0}.bus.add.xml".format(sys.argv[1]),
            output_file="person_trips.xml",
            num_trips=50,
            fixed_stop_id="bs_23",   # must exist in bus.add.xml
            fixed_stop_percentage=0.5,
            depart_time_range=(0, 3600)  # departures between 0–1h
        )
