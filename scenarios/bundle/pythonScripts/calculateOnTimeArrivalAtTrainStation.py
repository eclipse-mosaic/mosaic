import csv, setupTables
from mysql.connector.pooling import PooledMySQLConnection

my_db_connection: PooledMySQLConnection

def calculate_waiting_times(start_time, period, end_time, on_time_threshold, output_file):
    cursor = my_db_connection.cursor(dictionary=True)

    # === Fetch completed_seconds per order ===
    cursor.execute("""
                   SELECT id AS order_id, completed_seconds
                   FROM taxi_order
                   WHERE completed_seconds IS NOT NULL
                   ORDER BY completed_seconds
                   """)
    orders = cursor.fetchall()
    my_db_connection.close()

    # === Generate train arrival times ===
    train_times = []
    t = start_time
    while t <= end_time:
        train_times.append(t)
        t += period

    print(f"Generated {len(train_times)} train arrivals between {start_time} and {end_time}")

    # === Compute waiting times ===
    results = []
    for order in orders:
        order_id = order["order_id"]
        completed = order["completed_seconds"]

        # Find the next train time >= completed_seconds
        next_train = next((t for t in train_times if t >= completed), None)

        if next_train is None:
            # No train after this completion
            continue

        waiting_time = next_train - completed
        on_time = waiting_time > on_time_threshold

        results.append((order_id, completed, next_train, waiting_time, on_time))

    # === Save to CSV ===
    with open(output_file, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["order_id", "completed_seconds", "next_train_time", "waiting_time", "on_time"])
        writer.writerows(results)

    print(f"Saved {len(results)} waiting time results to {output_file}")

if __name__ == "__main__":
    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    start_time = 500
    period = 500
    end_time = 3500
    on_time_threshold = 120
    output_file = 'csv/onTimeArrival.csv'

    calculate_waiting_times(start_time, period, end_time, on_time_threshold, output_file)
