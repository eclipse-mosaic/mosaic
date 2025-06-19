import os
from mysql import connector
from mysql.connector.pooling import PooledMySQLConnection

DROP_TABLE_NAMES_ORDER = ['taxi_order', 'leg', 'route', 'cab', 'customer', 'freetaxi_order', 'stat', 'stop']
DELETE_TABLE_NAMES_ORDER = ['taxi_order', 'leg', 'route', 'cab', 'customer', 'freetaxi_order', 'stop']
my_db_connection: PooledMySQLConnection

# === Connect to the DB and create table ===
def setup_db_connection():
    global my_db_connection
    my_db_connection = connector.connect(
        host="localhost",
        user="kabina",
        password="kaboot",
        database="kabina"
    )

    if 'my_db_connection' not in globals():
        raise Exception("DB connection was not initialized")
    else:
        print("DB connection initialized!")

def drop_tables():
    cursor = my_db_connection.cursor()
    for table_name in DROP_TABLE_NAMES_ORDER:
        # drop existing table
        cursor.execute("DROP TABLE IF EXISTS {}".format(table_name))

def create_table_by_query(create_table_query, table_name):
    my_db_connection.cursor().execute(create_table_query)
    print("Table {} created".format(table_name))

def fill_stat_table():
    insert_stats_query = "INSERT INTO stat (name, int_val) VALUES (%s, %s)"
    cursor = my_db_connection.cursor()
    val = [
        ('AvgExtenderTime', 0),
        ('AvgPoolTime', 0),
        ('AvgPool3Time', 0),
        ('AvgPool4Time', 0),
        ('AvgPool5Time', 0),
        ('AvgLcmTime', 0),
        ('AvgSolverTime', 0),
        ('AvgSchedulerTime', 0),
        ('MaxExtenderTime', 0),
        ('MaxPoolTime', 0),
        ('MaxPool3Time', 0),
        ('MaxPool4Time', 0),
        ('MaxPool5Time', 0),
        ('MaxLcmTime', 0),
        ('MaxSolverTime', 0),
        ('MaxSchedulerTime', 0),
        ('AvgDemandSize', 0),
        ('AvgPoolDemandSize', 0),
        ('AvgSolverDemandSize', 0),
        ('MaxPoolDemandSize', 0),
        ('MaxSolverDemandSize', 0),
        ('AvgOrderAssignTime', 0),
        ('AvgOrderPickupTime', 0),
        ('AvgOrderCompleteTime', 0),
        ('TotalLcmUsed', 0),
        ('TotalPickupDistance', 0)
    ]
    cursor.executemany(insert_stats_query, val)
    my_db_connection.commit()
    print("Inserted {} values in stat table".format(cursor.rowcount))

def reset_tables():
    cursor = my_db_connection.cursor()
    cursor.execute("UPDATE stat SET int_val=0")
    my_db_connection.commit()

    for table_name in DELETE_TABLE_NAMES_ORDER:
        print("Resetting table {}".format(table_name))
        cursor.execute("DELETE FROM {}".format(table_name))
        my_db_connection.commit()
        cursor.execute("ALTER TABLE {} AUTO_INCREMENT = 1".format(table_name))
        my_db_connection.commit()
    print("All tables reset!")

def main(should_reset):
    print("Executing script:", os.path.basename(__file__))
    setup_db_connection()

    if should_reset:
        reset_tables()
        return
    else:
        drop_tables()

    # CAB_TABLE
    create_cab_table_query = ("CREATE TABLE cab (id BIGINT PRIMARY KEY AUTO_INCREMENT, location INTEGER NOT NULL, "
                              "name VARCHAR(255), status INTEGER NOT NULL, seats INTEGER NOT NULL)")
    create_table_by_query(create_cab_table_query, 'cab')

    # CUSTOMER_TABLE
    create_customer_table_query = "CREATE TABLE customer (id bigint AUTO_INCREMENT PRIMARY KEY, sumo_id varchar(255) NOT NULL)"
    create_table_by_query(create_customer_table_query, 'customer')

    # ROUTE_TABLE
    create_route_table_query = ("CREATE TABLE route (id bigint PRIMARY KEY, status integer NOT NULL, cab_id bigint NOT NULL, "
                                "locked boolean, FOREIGN KEY (cab_id) REFERENCES cab(id))")
    create_table_by_query(create_route_table_query, 'route')

    # LEG_TABLE
    create_leg_table_query = ("CREATE TABLE leg (id bigint PRIMARY KEY, completed timestamp, distance integer NOT NULL, "
                              "from_stand integer NOT NULL, place integer NOT NULL, started timestamp, status integer NOT NULL, "
                              "reserve integer NOT NULL, passengers integer NOT NULL, to_stand integer NOT NULL, route_id bigint NOT NULL, "
                              "FOREIGN KEY (route_id) REFERENCES route(id))")
    create_table_by_query(create_leg_table_query, 'leg')

    # TAXI_ORDER_TABLE
    create_taxi_order_table_query = ("CREATE TABLE taxi_order (id bigint PRIMARY KEY AUTO_INCREMENT, at_time timestamp, completed timestamp, "
                                     "distance integer NOT NULL, eta integer, from_stand integer NOT NULL, in_pool boolean, max_loss integer NOT NULL, "
                                     "max_wait integer NOT NULL, received timestamp, shared boolean NOT NULL, started timestamp, status integer, "
                                     "to_stand integer NOT NULL, cab_id bigint, customer_id bigint, leg_id bigint, route_id bigint, "
                                     "FOREIGN KEY (cab_id) REFERENCES cab(id), FOREIGN KEY (customer_id) REFERENCES customer(id), "
                                     "FOREIGN KEY (leg_id) REFERENCES leg(id), FOREIGN KEY (route_id) REFERENCES route(id))")
    create_table_by_query(create_taxi_order_table_query, 'taxi_order')

    # FREETAXI_ORDER_TABLE
    create_freetaxi_order_table_query = ("CREATE TABLE freetaxi_order (id bigint PRIMARY KEY AUTO_INCREMENT, from_stand integer NOT NULL, "
                                         "to_stand integer NOT NULL, max_loss integer NOT NULL, received timestamp, shared boolean NOT NULL, "
                                         "cab_id bigint, customer_id bigint)")
    create_table_by_query(create_freetaxi_order_table_query, 'freetaxi_order')

    # STAT_TABLE
    create_stat_table_query = "CREATE TABLE stat (name character varying(255) PRIMARY KEY, int_val integer NOT NULL)"
    create_table_by_query(create_stat_table_query, 'stat')
    fill_stat_table()

    # STOP_TABLE
    create_stop_table_query = ("CREATE TABLE stop (id bigint AUTO_INCREMENT PRIMARY KEY, bearing integer, "
                               "latitude double NOT NULL, longitude double NOT NULL, name varchar(255), "
                               "no varchar(255), type varchar(255), capacity integer NOT NULL, sumo_edge varchar(255))")
    create_table_by_query(create_stop_table_query, 'stop')

    print('All tables created!')
    my_db_connection.close()

if __name__ == "__main__":
    main(True) # Change this to False if you want to recreate all tables
