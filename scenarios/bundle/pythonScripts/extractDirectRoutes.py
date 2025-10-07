import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_empty_cruise_ratio(output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    cursor.execute("SELECT from_stand, completed_seconds FROM taxi_order")
    stand_to_time = {row["from_stand"]: row["completed_seconds"] for row in cursor.fetchall()}
    my_db_connection.close()

    # Compute ratios
    results = []
    for stand_id, direct_time_seconds in stand_to_time.items():
        results.append((stand_id, direct_time_seconds - 2))

    results.sort(key=lambda x: x[0])

    # Write results to CSV
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["stop_id", "direct_distance_seconds"])
        writer.writerows(results)

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/directRoutes.csv'
    calculate_empty_cruise_ratio(output_file)
