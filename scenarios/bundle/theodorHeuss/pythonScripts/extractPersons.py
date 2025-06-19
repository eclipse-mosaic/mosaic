import os
import setupTables
from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection
# Path to your SUMO rou.xml file
ROU_FILE = "../sumo/theodorHeuss.rou.xml"

# === Insert the person into the DB ===
def add_person_to_db(person_sumo_id):
    insert_values_query = "INSERT INTO customer (sumo_id) VALUES ('{}')".format(person_sumo_id)
    my_db_connection.cursor().execute(insert_values_query)
    my_db_connection.commit()

def main():
    print("Executing script:", os.path.basename(__file__))
    global my_db_connection
    # === Parse the rou.xml file ===
    rou_tree = etree.parse(ROU_FILE)

    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    # === Parse persons and add them to the DB ===
    for person in rou_tree.findall(".//person"):
        person_sumo_id = person.attrib["id"]
        add_person_to_db(person_sumo_id)

    print("Persons inserted into the DB!")
    my_db_connection.close()

if __name__ == "__main__":
    main()
