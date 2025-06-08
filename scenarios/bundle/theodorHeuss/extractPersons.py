from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection
import setupTables

my_db_connection: PooledMySQLConnection

# === Insert the person into the DB ===
def add_person_to_db(person_sumo_id):
    insert_values_query = "INSERT INTO customer (sumo_id) VALUES ('{}')".format(person_sumo_id)
    my_db_connection.cursor().execute(insert_values_query)
    my_db_connection.commit()

# === Input files ===
# Path to your SUMO rou.xml file
ROU_FILE = "sumo/theodorHeuss.rou.xml"

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
