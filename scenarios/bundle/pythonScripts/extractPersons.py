import os, setupTables, sys
from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

# === Insert the person into the DB ===
def add_person_to_db(person_sumo_id):
    insert_values_query = "INSERT INTO customer (sumo_id) VALUES ('{}')".format(person_sumo_id)
    my_db_connection.cursor().execute(insert_values_query)
    my_db_connection.commit()

def main(scenario_name: str):
    print("Executing script:", os.path.basename(__file__))
    global my_db_connection

    # === Parse the rou.xml file ===
    rou_file = "../{0}/sumo/{0}.persons.rou.xml".format(scenario_name)
    rou_tree = etree.parse(rou_file)

    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    # === Parse persons and add them to the DB ===
    for person in rou_tree.findall(".//person"):
        person_sumo_id = person.attrib["id"]
        add_person_to_db(person_sumo_id)

    print("Persons inserted into the DB!")
    my_db_connection.close()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        main(sys.argv[1])
