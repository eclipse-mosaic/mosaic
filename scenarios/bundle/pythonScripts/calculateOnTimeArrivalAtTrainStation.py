import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_waiting_times(on_time_threshold, output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    # === Fetch completed_seconds per order ===
    cursor.execute("""
                   SELECT id AS order_id, completed_seconds, train_to_catch
                   FROM taxi_order
                   WHERE completed_seconds IS NOT NULL
                   ORDER BY completed_seconds
                   """)
    orders = cursor.fetchall()
    my_db_connection.close()

    # === Compute waiting times ===
    results = []
    for order in orders:
        order_id = order["order_id"]
        completed = order["completed_seconds"]
        train_to_catch = order["train_to_catch"]

        waiting_time = train_to_catch - completed
        on_time = waiting_time > on_time_threshold
        if not on_time:
            waiting_time = ''

        results.append((order_id, completed, train_to_catch, waiting_time, on_time))

    # === Save to CSV ===
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "completed_seconds", "next_train_time", "waiting_time", "on_time"])
        writer.writerows(results)

    print(f"Saved {len(results)} waiting time results to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    on_time_threshold = 60
    output_file = 'csv/passengers_300/onTimeArrival.csv'

    calculate_waiting_times(on_time_threshold, output_file)
