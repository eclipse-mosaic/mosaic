import os
from mysql import connector
from mysql.connector.pooling import PooledMySQLConnection

DROP_TABLE_NAMES_ORDER = ['taxi_order', 'leg', 'route', 'cab', 'customer', 'freetaxi_order', 'stat', 'stop']
DELETE_TABLE_NAMES_ORDER = ['taxi_order', 'leg', 'route', 'cab', 'customer', 'freetaxi_order', 'stop']
STAT_KEYS = [
    "AvgExtenderTime", "AvgPoolTime", "AvgPool3Time", "AvgPool4Time", "AvgPool5Time",
    "AvgLcmTime", "AvgSolverTime", "AvgSchedulerTime",
    "MaxExtenderTime", "MaxPoolTime", "MaxPool3Time", "MaxPool4Time", "MaxPool5Time",
    "MaxLcmTime", "MaxSolverTime", "MaxSchedulerTime",
    "AvgDemandSize", "AvgPoolDemandSize", "AvgSolverDemandSize",
    "MaxPoolDemandSize", "MaxSolverDemandSize",
    "AvgOrderAssignTime", "AvgOrderPickupTime", "AvgOrderCompleteTime",
    "TotalLcmUsed", "TotalPickupDistance"
]

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
    with my_db_connection.cursor() as cursor:
        for table_name in DROP_TABLE_NAMES_ORDER:
            cursor.execute("DROP TABLE IF EXISTS {}".format(table_name))
    print("Tables dropped.")

def create_table_by_query(create_table_query, table_name):
    with my_db_connection.cursor() as cursor:
        cursor.execute(create_table_query)
    print(f"Table {table_name} created")

def fill_stat_table():
    insert_stats_query = "INSERT INTO stat (name, int_val) VALUES (%s, %s)"
    values = [(key, 0) for key in STAT_KEYS]
    with my_db_connection.cursor() as cursor:
        cursor.executemany(insert_stats_query, values)
    my_db_connection.commit()
    print(f"Inserted {len(values)} values in stat table")

def reset_tables():
    with my_db_connection.cursor() as cursor:
        cursor.execute("UPDATE stat SET int_val=0")

        for table_name in DELETE_TABLE_NAMES_ORDER:
            print(f"Resetting table {table_name}")
            cursor.execute(f"DELETE FROM {table_name}")
            cursor.execute(f"ALTER TABLE {table_name} AUTO_INCREMENT = 1")

    my_db_connection.commit()
    print("All tables reset!")

def create_tables():
    queries = {
        "cab": """
            CREATE TABLE cab (
                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                 location INT NOT NULL,
                 name VARCHAR(255),
                 status INT NOT NULL,
                 seats INT NOT NULL
            )
        """,
        "customer": """
            CREATE TABLE customer (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                sumo_id VARCHAR(255) NOT NULL
            )
        """,
        "route": """
            CREATE TABLE route (
                id BIGINT PRIMARY KEY,
                status INT NOT NULL,
                cab_id BIGINT NOT NULL,
                locked BOOLEAN,
                FOREIGN KEY (cab_id) REFERENCES cab(id)
            )
        """,
        "leg": """
            CREATE TABLE leg (
                id BIGINT PRIMARY KEY,
                completed TIMESTAMP,
                distance INT NOT NULL,
                from_stand INT NOT NULL,
                place INT NOT NULL,
                started TIMESTAMP,
                status INT NOT NULL,
                reserve INT NOT NULL,
                passengers INT NOT NULL,
                to_stand INT NOT NULL,
                route_id BIGINT NOT NULL,
                started_seconds BIGINT,
                completed_seconds BIGINT,
                FOREIGN KEY (route_id) REFERENCES route(id)
            )
        """,
        "taxi_order": """
            CREATE TABLE taxi_order (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                sumo_id BIGINT NOT NULL,
                at_time TIMESTAMP,
                completed TIMESTAMP,
                distance INT NOT NULL,
                eta INT,
                from_stand INT NOT NULL,
                in_pool BOOLEAN,
                max_loss INT NOT NULL,
                max_wait INT NOT NULL,
                received TIMESTAMP,
                shared BOOLEAN NOT NULL,
                started TIMESTAMP,
                status INT,
                to_stand INT NOT NULL,
                cab_id BIGINT,
                customer_id BIGINT,
                leg_id BIGINT,
                route_id BIGINT,
                distance_seconds INT NOT NULL,
                received_seconds BIGINT,
                started_seconds BIGINT,
                completed_seconds BIGINT,
                train_to_catch INT,
                FOREIGN KEY (cab_id) REFERENCES cab(id),
                FOREIGN KEY (customer_id) REFERENCES customer(id),
                FOREIGN KEY (leg_id) REFERENCES leg(id),
                FOREIGN KEY (route_id) REFERENCES route(id)
            )
        """,
        "freetaxi_order": """
            CREATE TABLE freetaxi_order (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                from_stand INT NOT NULL,
                to_stand INT NOT NULL,
                max_loss INT NOT NULL,
                received TIMESTAMP,
                shared BOOLEAN NOT NULL,
                cab_id BIGINT,
                customer_id BIGINT
            )
        """,
        "stat": """
            CREATE TABLE stat (
                name VARCHAR(255) PRIMARY KEY,
                int_val INT NOT NULL
            )
        """,
        "stop": """
            CREATE TABLE stop (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                bearing INT,
                latitude DOUBLE NOT NULL,
                longitude DOUBLE NOT NULL,
                name VARCHAR(255),
                no VARCHAR(255),
                type VARCHAR(255),
                capacity INT NOT NULL,
                sumo_edge VARCHAR(255)
            )
        """,
    }

    for name, query in queries.items():
        create_table_by_query(query, name)

    fill_stat_table()
    print("All tables created!")

def main(should_reset):
    print("Executing script:", os.path.basename(__file__))
    setup_db_connection()

    if should_reset:
        reset_tables()
    else:
        drop_tables()
        create_tables()

    my_db_connection.close()

if __name__ == "__main__":
    main(True) # Change this to False if you want to recreate all tables
