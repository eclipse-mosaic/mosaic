import csv, setupTables
from datetime import datetime
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_empty_cruise_ratio(output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    # Fetch route_id -> cab_id mapping
    cursor.execute("SELECT DISTINCT route_id, cab_id FROM taxi_order")
    route_to_cab = {row["route_id"]: row["cab_id"] for row in cursor.fetchall()}

    # Fetch all legs with times and passenger counts
    cursor.execute("""
                   SELECT id, route_id, passengers, started, completed
                   FROM leg
                   """)
    legs = cursor.fetchall()
    my_db_connection.close()

    cab_stats = {}
    for leg in legs:
        route_id = leg["route_id"]
        cab_id = route_to_cab.get(route_id)

        if not cab_id or not leg["started"] or not leg["completed"]:
            continue

        start_time = leg["started"]
        end_time = leg["completed"]

        # Ensure datetime
        if isinstance(start_time, str):
            start_time = datetime.fromisoformat(start_time)
        if isinstance(end_time, str):
            end_time = datetime.fromisoformat(end_time)

        duration = (end_time - start_time).total_seconds()

        if cab_id not in cab_stats:
            cab_stats[cab_id] = {"empty_time": 0, "total_time": 0}

        cab_stats[cab_id]["total_time"] += duration
        if leg["passengers"] == 0:
            cab_stats[cab_id]["empty_time"] += duration

    # Compute ratios
    results = []
    for cab_id, stats in cab_stats.items():
        total = stats["total_time"]
        empty = stats["empty_time"]
        ratio = (empty / total) * 100 if total > 0 else 0
        results.append((cab_id, ratio))

    # Write results to CSV
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["cab_id", "empty_cruise_ratio"])
        writer.writerows(results)

    print(f"Empty cruise ratios written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/emptyCruiseRatio.csv'
    calculate_empty_cruise_ratio(output_file)
