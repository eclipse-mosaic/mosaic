import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_pooling_efficiency(output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    # 1. Get total number of trips (unique route_ids in taxi_order)
    cursor.execute("SELECT COUNT(DISTINCT route_id) AS total_trips FROM taxi_order WHERE route_id IS NOT NULL ")
    total_trips = cursor.fetchone()["total_trips"]

    # 2. Get number of trips with pooling (legs with passengers > 1)
    cursor.execute("""
                   SELECT COUNT(DISTINCT route_id) AS pooled_trips
                   FROM leg
                   WHERE passengers > 1
                   """)
    pooled_trips = cursor.fetchone()["pooled_trips"]

    my_db_connection.close()

    # 3. Compute efficiency
    efficiency = round((pooled_trips / total_trips) * 100, 2) if total_trips > 0 else 0

    # 4. Write result to CSV
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["total_trips", "pooled_trips", "pooling_efficiency_percentage"])
        writer.writerow([total_trips, pooled_trips, efficiency])

    print(f"Pooling efficiency written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/passengers_500/poolingEfficiency.csv'
    calculate_pooling_efficiency(output_file)
