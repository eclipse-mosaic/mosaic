import xml.etree.ElementTree as ET
import random, sys, os
import xml.dom.minidom as minidom

def generate_taxi_vehicles(
        bus_add_file,
        output_file,
        num_vehicles=10
):
    """
    Generate a SUMO vehicle XML file with taxis placed at random bus stops.

    Parameters
    ----------
    bus_add_file : str
        Path to bus.add.xml containing <busStop> elements.
    output_file : str
        Path to write the generated vehicles XML.
    num_vehicles : int
        Number of taxi vehicles to create.
    """

    # Parse bus stops from bus.add.xml
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

    if num_vehicles > len(stops):
        raise ValueError("Requested more vehicles than available bus stops.")

    # Randomly select bus stops
    selected_stops = random.sample(stops, num_vehicles)

    # Create root element
    routes = ET.Element("routes")

    # Generate vehicles
    for i, stop in enumerate(selected_stops, start=1):
        edge = stop["lane"][:-2]  # strip last two characters

        vehicle = ET.SubElement(routes, "vehicle", {
            "id": f"t{i}",
            "type": "TaxiSumo",
            "depart": "0",
            "departPos": "free",
            "line": "taxi"
        })
        ET.SubElement(vehicle, "route", {
            "edges": edge
        })

    # Convert to string and pretty print
    xml_str = ET.tostring(routes, encoding="utf-8")
    parsed = minidom.parseString(xml_str)
    pretty_xml_str = parsed.toprettyxml(indent="  ")

    # Write to file
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(pretty_xml_str)

    print(f"Generated {num_vehicles} taxi vehicles in {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        generate_taxi_vehicles(
            bus_add_file="../{0}/sumo/{0}.bus.add.xml".format(sys.argv[1]),
            output_file="generated_taxis.xml",
            num_vehicles=20
        )
