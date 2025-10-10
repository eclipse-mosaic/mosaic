import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_waiting_times(output_file):
    results = []

    with my_db_connection.cursor(dictionary=True) as cursor:
        cursor.execute("SELECT id, route_id, from_stand, received_seconds FROM taxi_order WHERE status!=6")
        orders = cursor.fetchall()

        for order in orders:
            order_id = order["id"]
            route_id = order["route_id"]
            from_stand = order["from_stand"]
            received_seconds = order["received_seconds"]

            # First leg (from_stand)
            cursor.execute("""
                           SELECT started_seconds
                           FROM leg
                           WHERE route_id = %s AND from_stand = %s
                           ORDER BY id LIMIT 1
                           """, (route_id, from_stand))
            first = cursor.fetchone()

            if not first or first["started_seconds"] is None:
                print(f"Skipping order {order_id}: missing leg times")
                continue

            start_time = first["started_seconds"]

            # Compute wait time
            wait_time = start_time - received_seconds

            results.append((order_id, wait_time))

    my_db_connection.close()

    # Write results to file
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "wait_time_seconds"])
        writer.writerows(results)

    print(f"Waiting times written to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    output_file = 'csv/passengers_100_2_15_70_10/waitingTime.csv'
    calculate_waiting_times(output_file)
