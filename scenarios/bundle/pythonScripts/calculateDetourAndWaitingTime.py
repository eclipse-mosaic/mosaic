import csv, setupTables
from datetime import datetime
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_detour_ratios(real_time_factor, output_file):
    results = []

    with my_db_connection.cursor(dictionary=True) as cursor:
        cursor.execute("SELECT id, route_id, from_stand, to_stand, received, distance_seconds FROM taxi_order")
        orders = cursor.fetchall()

        for order in orders:
            order_id = order["id"]
            route_id = order["route_id"]
            from_stand = order["from_stand"]
            to_stand = order["to_stand"]
            received = order["received"]
            distance_seconds = order["distance_seconds"]

            # First leg (from_stand)
            cursor.execute("""
                           SELECT started
                           FROM leg
                           WHERE route_id = %s AND from_stand = %s
                           ORDER BY id LIMIT 1
                           """, (route_id, from_stand))
            first = cursor.fetchone()

            # Last leg (to_stand)
            cursor.execute("""
                           SELECT completed
                           FROM leg
                           WHERE route_id = %s AND to_stand = %s
                           ORDER BY id DESC LIMIT 1
                           """, (route_id, to_stand))
            last = cursor.fetchone()

            if not first or not last or first["started"] is None or last["completed"] is None:
                print(f"Skipping order {order_id}: missing leg times")
                continue

            start_time = first["started"]
            end_time = last["completed"]

            # Ensure datetime
            if isinstance(received, str):
                received = datetime.fromisoformat(received)
            if isinstance(start_time, str):
                start_time = datetime.fromisoformat(start_time)
            if isinstance(end_time, str):
                end_time = datetime.fromisoformat(end_time)

            # Compute travel time (seconds) with Real Time Factor
            travel_time = (end_time - start_time).total_seconds() * real_time_factor
            wait_time = (start_time - received).total_seconds() if received and start_time else None

            detour_ratio = travel_time / distance_seconds
            results.append((order_id, detour_ratio, wait_time))

    my_db_connection.close()

    # Write results to file
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "detour_ratio", "wait_time_seconds"])  # header
        writer.writerows(results)

    print(f"Detour ratios written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    real_time_factor = 2.6 # simulation time duration / wall-clock-time duration
    output_file = 'csv/detourWaitingTime.csv'
    calculate_detour_ratios(real_time_factor, output_file)
