import csv
import setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def load_direct_routes(csv_path):
    """Load mapping of bus stop IDs to direct route distances."""
    routes = {}
    with open(csv_path, mode='r', newline='') as f:
        reader = csv.DictReader(f)
        for row in reader:
            bus_stop_id = row.get("stop_id")
            distance = float(row["direct_distance_seconds"])
            routes[str(bus_stop_id)] = distance
    return routes

def calculate_detour_ratios(output_file, direct_routes_csv):
    results = []
    direct_routes = load_direct_routes(direct_routes_csv)

    with my_db_connection.cursor(dictionary=True) as cursor:
        # fetch orders only from grouped routes
        cursor.execute("""
                       SELECT id, route_id, from_stand, to_stand
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

            distance_seconds = direct_routes.get(str(from_stand))

            if distance_seconds is None:
                print(f"Skipping order {order_id}: no distance found for bus stop {from_stand}")
                continue

            # first leg (from_stand)
            cursor.execute("""
                           SELECT started_seconds, id, passengers
                           FROM leg
                           WHERE route_id = %s AND from_stand = %s AND passengers > 0
                           ORDER BY id LIMIT 1
                           """, (route_id, from_stand))
            first = cursor.fetchone()

            # last leg (to_stand)
            cursor.execute("""
                           SELECT completed_seconds, id, passengers
                           FROM leg
                           WHERE route_id = %s AND to_stand = %s AND passengers > 0
                           ORDER BY id DESC LIMIT 1
                           """, (route_id, to_stand))
            last = cursor.fetchone()

            if not first or not last or first["started_seconds"] is None or last["completed_seconds"] is None:
                print(f"Skipping order {order_id}: missing leg times")
                continue

            if last["id"] == first["id"]:
                print(f"Skipping order {order_id}: uses its direct route")
                continue

            start_time = first["started_seconds"]
            end_time = last["completed_seconds"]

            travel_time = end_time - start_time

            detour_ratio = (round(travel_time * 100 / distance_seconds, 0)) % 100 if distance_seconds else None
            results.append((order_id, detour_ratio))

    my_db_connection.close()

    # write results
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "detour_ratio_percents"])
        writer.writerows(results)

    print(f"Detour ratios (only grouped orders) written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/passengers_100_2_15_70_10/detourRatio.csv'
    direct_routes_csv = 'csv/directRoutes.csv'
    calculate_detour_ratios(output_file, direct_routes_csv)
