import os, setupTables, sys

from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection
UNKNOWN_TAXI_STATUS = 2
TAXI_CAPACITY: int

def get_stop_id_from_edge(edge: str):
    """
    Fetch the stop.id where stop.sumo_edge = given edge.
    Returns None if not found.
    """
    try:
        cursor = my_db_connection.cursor(dictionary=True)
        cursor.execute("SELECT id FROM stop WHERE sumo_edge = %s LIMIT 1", (edge,))
        row = cursor.fetchone()
        cursor.close()
        return row["id"] if row else None
    except Exception as e:
        print(f"Error fetching stop ID for edge {edge}: {e}")
        return None

# === Insert the cab into the DB ===
def add_cab_to_db(stop_id: int, cab_sumo_id: str):
    insert_values_query = "INSERT INTO cab (location, name, status, seats) VALUES ('{}','{}',{},{})".format(stop_id, cab_sumo_id, UNKNOWN_TAXI_STATUS, TAXI_CAPACITY)
    my_db_connection.cursor().execute(insert_values_query)
    my_db_connection.commit()

def main(scenario_name: str):
    print("Executing script:", os.path.basename(__file__))
    global my_db_connection, TAXI_CAPACITY

    # === Parse the rou.xml file ===
    rou_file = "../{0}/sumo/{0}.pt.rou.xml".format(scenario_name)
    rou_tree = etree.parse(rou_file)

    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    # === Get taxi capacity ===
    TAXI_CAPACITY = None
    for vehicleType in rou_tree.findall(".//vType"):
        if vehicleType.attrib["vClass"] == "taxi":
            TAXI_CAPACITY = vehicleType.attrib["personCapacity"]
            break

    # === Parse cabs and add them to the DB ===
    for cab in rou_tree.findall(".//vehicle"):
        cab_sumo_id = cab.attrib["id"]

        # Each <vehicle> has its own <route> child
        route_elem = cab.find(".//route")
        if route_elem is None or "edges" not in route_elem.attrib:
            print(f"Skipping {cab_sumo_id}: missing route/edges")
            continue

        edge = route_elem.attrib["edges"].strip()
        stop_id = get_stop_id_from_edge(edge)

        if stop_id is None:
            print(f"Warning: No stop found for edge {edge} (vehicle {cab_sumo_id})")
            continue

        add_cab_to_db(stop_id, cab_sumo_id)

    print("Cabs inserted into the DB!")
    my_db_connection.close()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        main(sys.argv[1])
