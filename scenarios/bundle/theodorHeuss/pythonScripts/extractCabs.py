import os, setupTables
from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection
# Path to your SUMO rou.xml file
ROU_FILE = "../sumo/theodorHeuss.rou.xml"
UNKNOW_TAXI_STATUS = 2
TAXI_CAPACITY: int

# === Insert the cab into the DB ===
def add_cab_to_db(cab_sumo_id):
    insert_values_query = "INSERT INTO cab (location, name, status, seats) VALUES (1,'{}',{},{})".format(cab_sumo_id, UNKNOW_TAXI_STATUS, TAXI_CAPACITY)
    my_db_connection.cursor().execute(insert_values_query)
    my_db_connection.commit()

def main():
    print("Executing script:", os.path.basename(__file__))
    global my_db_connection, TAXI_CAPACITY
    # === Parse the rou.xml file ===
    rou_tree = etree.parse(ROU_FILE)

    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    for vehicleType in rou_tree.findall(".//vType"):
        if vehicleType.attrib["vClass"] == "taxi":
            TAXI_CAPACITY = vehicleType.attrib["personCapacity"]
            break

    # === Parse persons and add them to the DB ===
    for cab in rou_tree.findall(".//vehicle"):
        cab_sumo_id = cab.attrib["id"]
        add_cab_to_db(cab_sumo_id)

    print("Cabs inserted into the DB!")
    my_db_connection.close()

if __name__ == "__main__":
    main()
