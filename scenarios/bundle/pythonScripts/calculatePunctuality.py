import pandas as pd

def calculate_punctuality():
    folder_names= ['300', '500', '700', '100_normal', '100_2_50_5', '100_2_15_70_10']
    file_paths = []

    for folder_name in folder_names:
        file_paths.append(f'./csv/passengers_{folder_name}/{file_name}')

    for file_path in file_paths:
        # Load the CSV file
        df = pd.read_csv(file_path)

        # Count how many rows have on_time == True
        true_count = df["on_time"].sum()
        false_count = len(df) - true_count

        print(f"Number of True values in {file_path}: {round(true_count * 100 / (true_count + false_count))}")

if __name__ == "__main__":
    file_name = 'onTimeArrival.csv'
    calculate_punctuality()
