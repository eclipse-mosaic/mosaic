import csv
import setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_detour_ratios(output_file):
    results = []

    with my_db_connection.cursor(dictionary=True) as cursor:
        # fetch orders only from grouped routes
        cursor.execute("""
                       SELECT id, route_id, from_stand, to_stand, distance_seconds
                       FROM taxi_order
                       WHERE route_id IN (
                           SELECT route_id
                           FROM leg
                           WHERE passengers > 1
                           GROUP BY route_id
                       )
                       """)
        orders = cursor.fetchall()

        if not orders:
            print("No grouped orders found.")
            return

        # compute detour ratio
        for order in orders:
            order_id = order["id"]
            route_id = order["route_id"]
            from_stand = order["from_stand"]
            to_stand = order["to_stand"]
            distance_seconds = order["distance_seconds"]

            # first leg (from_stand)
            cursor.execute("""
                           SELECT started_seconds
                           FROM leg
                           WHERE route_id = %s AND from_stand = %s
                           ORDER BY id LIMIT 1
                           """, (route_id, from_stand))
            first = cursor.fetchone()

            # last leg (to_stand)
            cursor.execute("""
                           SELECT completed_seconds
                           FROM leg
                           WHERE route_id = %s AND to_stand = %s
                           ORDER BY id DESC LIMIT 1
                           """, (route_id, to_stand))
            last = cursor.fetchone()

            if not first or not last or first["started_seconds"] is None or last["completed_seconds"] is None:
                print(f"Skipping order {order_id}: missing leg times")
                continue

            start_time = first["started_seconds"]
            end_time = last["completed_seconds"]

            # account for fixed pickup/drop-off durations
            pick_up_duration = 20
            drop_off_duration = 60

            travel_time = end_time - start_time - pick_up_duration - drop_off_duration

            detour_ratio = travel_time / distance_seconds if distance_seconds else None
            results.append((order_id, round(detour_ratio, 2)))

    my_db_connection.close()

    # write results
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "detour_ratio"])
        writer.writerows(results)

    print(f"Detour ratios (only grouped orders) written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/detourRatio.csv'
    calculate_detour_ratios(output_file)
