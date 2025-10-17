import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_operating_ratios(total_simulation_time_seconds, output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    # 1. Fetch route_id -> cab_id mapping
    cursor.execute("SELECT DISTINCT route_id, cab_id FROM taxi_order WHERE route_id IS NOT NULL AND cab_id IS NOT NULL")
    route_to_cab = {row["route_id"]: row["cab_id"] for row in cursor.fetchall()}

    # 2. Fetch legs grouped by route_id
    cursor.execute("""
                   SELECT route_id, MIN(started_seconds) AS start_time, MAX(completed_seconds) AS end_time
                   FROM leg
                   GROUP BY route_id
                   """)
    route_times = cursor.fetchall()

    my_db_connection.close()

    # 3. Aggregate operating times per cab
    cab_stats = {}
    for row in route_times:
        route_id = row["route_id"]
        cab_id = route_to_cab.get(route_id)

        if not cab_id or not row["start_time"] or not row["end_time"]:
            continue

        start_time = row["start_time"]
        end_time = row["end_time"]

        operating_time = end_time - start_time

        if cab_id not in cab_stats:
            cab_stats[cab_id] = 0
        cab_stats[cab_id] += operating_time

    # 4. Compute ratios
    results = []
    for cab_id, total_operating_time in cab_stats.items():
        ratio = round((total_operating_time / total_simulation_time_seconds) * 100, 2) if total_simulation_time_seconds > 0 else 0
        results.append((cab_id, ratio))

    results.sort(key=lambda x: x[0])

    # 5. Write results to CSV
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["cab_id", "operating_time_ratio_percentage"])
        writer.writerows(results)

    print(f"Utilization rates written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    total_simulation_time_seconds = 1500
    output_file = 'csv/paramset_3/utilizationRate.csv'
    calculate_operating_ratios(total_simulation_time_seconds, output_file)
