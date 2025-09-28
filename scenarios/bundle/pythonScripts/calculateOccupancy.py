import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_avg_passengers(output_file):

    with my_db_connection.cursor(dictionary=True) as cursor:
        # Get all orders grouped by route_id
        cursor.execute("""
                       SELECT route_id, cab_id, COUNT(*) AS passenger_count
                       FROM taxi_order
                       GROUP BY route_id, cab_id
                       """)
        route_groups = cursor.fetchall()

    my_db_connection.close()

    # Organize passenger counts per cab
    cab_stats = {}
    for row in route_groups:
        cab_id = row["cab_id"]
        passenger_count = row["passenger_count"]

        if cab_id not in cab_stats:
            cab_stats[cab_id] = {"total_passengers": 0, "num_groups": 0}

        cab_stats[cab_id]["total_passengers"] += passenger_count
        cab_stats[cab_id]["num_groups"] += 1

    # Compute averages
    results = []
    for cab_id, stats in cab_stats.items():
        avg_passengers = stats["total_passengers"] / stats["num_groups"] if stats["num_groups"] > 0 else 0
        results.append((cab_id, avg_passengers))

    # Write results to CSV
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["cab_id", "avg_occupancy"])
        writer.writerows(results)

    print(f"Average passenger stats written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/averageOccupancy.csv'
    calculate_avg_passengers(output_file)
