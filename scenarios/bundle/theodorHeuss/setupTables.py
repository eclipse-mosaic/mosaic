from mysql import connector

# === Connect to the DB and create table ===
def setup_db_connection():
    mydb = connector.connect(
        host="localhost",
        user="kabina",
        password="kaboot",
        database="kabina"
    )
    return mydb

def drop_tables_by_name(table_names, database_connection):
    for table_name in table_names:
        # drop existing table
        database_connection.cursor().execute("DROP TABLE IF EXISTS {}".format(table_name))

def create_table_by_name_and_query(database_connection, table_name, create_table_query):
    database_connection.cursor().execute(create_table_query)

def fill_stat_table(database_connection):
    insert_stats_query = ("INSERT INTO stat (name, int_val) VALUES ('AvgExtenderTime', 0), ('AvgPoolTime', 0), "
                          "('AvgPool3Time', 0), ('AvgPool4Time', 0), ('AvgPool5Time', 0), ('AvgLcmTime', 0), "
                          "('AvgSolverTime', 0), ('AvgShedulerTime', 0), ('MaxExtenderTime', 0), ('MaxPoolTime', 0), "
                          "('MaxPool3Time', 0), ('MaxPool4Time', 0), ('MaxPool5Time', 0), ('MaxLcmTime', 0), ('MaxSolverTime', 0), "
                          "('MaxShedulerTime', 0), ('AvgDemandSize', 0), ('AvgPoolDemandSize', 0), ('AvgSolverDemandSize', 0), "
                          "('MaxDemandSize', 0), ('MaxPoolDemandSize', 0), ('MaxSolverDemandSize', 0), ('AvgOrderAssignTime', 0), "
                          "('AvgOrderPickupTime', 0), ('AvgOrderCompleteTime', 0), ('TotalLcmUsed', 0), ('TotalPickupDistance', 0)")
    database_connection.cursor().execute(insert_stats_query)
    database_connection.commit()

def restart_tables(database_connection):
    database_connection.cursor().execute("UPDATE stat SET int_val=0")
    database_connection.commit()
    database_connection.cursor().execute("DELETE FROM taxi_order")
    database_connection.commit()
    database_connection.cursor().execute("DELETE FROM leg")
    database_connection.commit()
    database_connection.cursor().execute("DELETE FROM route")
    database_connection.commit()

# === Start of script ===
mydb = setup_db_connection()

drop_tables_by_name(['taxi_order', 'leg', 'route', 'cab', 'customer', 'freetaxi_order', 'stat', 'stop'], mydb)

# CAB_TABLE
create_cab_table_query = ("CREATE TABLE cab (id BIGINT PRIMARY KEY, location INTEGER NOT NULL, name VARCHAR(255), "
                          "status INTEGER NOT NULL, seats INTEGER NOT NULL)")
create_table_by_name_and_query(mydb, 'cab', create_cab_table_query)

# CUSTOMER_TABLE
create_customer_table_query = "CREATE TABLE customer (id bigint AUTO_INCREMENT PRIMARY KEY, sumo_id varchar(255) NOT NULL)"
create_table_by_name_and_query(mydb, 'customer', create_customer_table_query)

# ROUTE_TABLE
create_route_table_query = ("CREATE TABLE route (id bigint PRIMARY KEY, status integer NOT NULL, cab_id bigint NOT NULL, "
                            "locked boolean, FOREIGN KEY (cab_id) REFERENCES cab(id))")
create_table_by_name_and_query(mydb, 'route', create_route_table_query)

# LEG_TABLE
create_leg_table_query = ("CREATE TABLE leg (id bigint PRIMARY KEY, completed timestamp, distance integer NOT NULL, "
                          "from_stand integer NOT NULL, place integer NOT NULL, started timestamp, status integer NOT NULL, "
                          "reserve integer NOT NULL, passengers integer NOT NULL, to_stand integer NOT NULL, route_id bigint NOT NULL, "
                          "FOREIGN KEY (route_id) REFERENCES route(id))")
create_table_by_name_and_query(mydb, 'leg', create_leg_table_query)

# TAXI_ORDER_TABLE
create_taxi_order_table_query = ("CREATE TABLE taxi_order (id bigint PRIMARY KEY AUTO_INCREMENT, at_time timestamp, completed timestamp, "
                                 "distance integer NOT NULL, eta integer, from_stand integer NOT NULL, in_pool boolean, max_loss integer NOT NULL, "
                                 "max_wait integer NOT NULL, received timestamp, shared boolean NOT NULL, started timestamp, status integer, "
                                 "to_stand integer NOT NULL, cab_id bigint, customer_id bigint, leg_id bigint, route_id bigint, "
                                 "FOREIGN KEY (cab_id) REFERENCES cab(id), FOREIGN KEY (customer_id) REFERENCES customer(id), "
                                 "FOREIGN KEY (leg_id) REFERENCES leg(id), FOREIGN KEY (route_id) REFERENCES route(id))")
create_table_by_name_and_query(mydb, 'taxi_order', create_taxi_order_table_query)

# FREETAXI_ORDER_TABLE
create_freetaxi_order_table_query = ("CREATE TABLE freetaxi_order (id bigint PRIMARY KEY AUTO_INCREMENT, from_stand integer NOT NULL, "
                                     "to_stand integer NOT NULL, max_loss integer NOT NULL, received timestamp, shared boolean NOT NULL, "
                                     "cab_id bigint, customer_id bigint)")
create_table_by_name_and_query(mydb, 'freetaxi_order', create_freetaxi_order_table_query)

# STAT_TABLE
create_stat_table_query = "CREATE TABLE stat (name character varying(255) PRIMARY KEY, int_val integer NOT NULL)"
create_table_by_name_and_query(mydb, 'stat', create_stat_table_query)
fill_stat_table(mydb)

# STOP_TABLE
create_stop_table_query = ("CREATE TABLE stop (id bigint AUTO_INCREMENT PRIMARY KEY, bearing integer, "
                           "latitude double NOT NULL, longitude double NOT NULL, name varchar(255), "
                           "no varchar(255), type varchar(255), capacity integer NOT NULL, sumo_edge varchar(255))")
create_table_by_name_and_query(mydb, 'stop', create_stop_table_query)

mydb.close()
