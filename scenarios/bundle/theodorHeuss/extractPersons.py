from lxml import etree
from setupTables import setup_db_connection

# === Input files ===
# Path to your SUMO rou.xml file
ROU_FILE = "sumo/theodorHeuss.rou.xml"

# === Parse the rou.xml file ===
rou_tree = etree.parse(ROU_FILE)

### Begin of help methods ###

# === Insert the person into the DB ===
def add_person_to_db(person_sumo_id, mydb):
    insert_values_query = "INSERT INTO customer (sumo_id) VALUES ('{}')".format(person_sumo_id)
    mydb.cursor().execute(insert_values_query)
    mydb.commit()

### End of help methods ###

mydb = setup_db_connection()

# === Parse persons and add them to the DB ===
for person in rou_tree.findall(".//person"):
    person_sumo_id = person.attrib["id"]

    add_person_to_db(person_sumo_id, mydb)

mydb.close()
