from lxml import etree
import mysql.connector

# === Input files ===
# Path to your SUMO rou.xml file
ROU_FILE = "sumo/theodorHeuss.rou.xml"

# === Parse the rou.xml file ===
rou_tree = etree.parse(ROU_FILE)

### Begin of help methods ###

# === Connect to the DB and create table ===
def setup_db():
    mydb = mysql.connector.connect(
        host="localhost",
        user="kabina",
        password="kaboot",
        database="kabina"
    )

    mycursor = mydb.cursor()

    # drop existing table
    drop_customer_table_query = "DROP TABLE IF EXISTS customer"
    mycursor.execute(drop_customer_table_query)

    # create new table
    create_customer_table_query = "CREATE TABLE customer (id bigint AUTO_INCREMENT PRIMARY KEY, sumo_id varchar(255) NOT NULL)"
    mycursor.execute(create_customer_table_query)

    return mydb

# === Insert the person into the DB ===
def add_person_to_db(person_sumo_id, mydb):
    insert_values_query = "INSERT INTO customer (sumo_id) VALUES ('{}')".format(person_sumo_id)
    mydb.cursor().execute(insert_values_query)
    mydb.commit()

### End of help methods ###

mydb = setup_db()

# === Parse persons and add them to the DB ===
for person in rou_tree.findall(".//person"):
    person_sumo_id = person.attrib["id"]

    add_person_to_db(person_sumo_id, mydb)
