import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

def show_data():
    file_paths = ['./../csv/passengers_100_normal/averageOccupancy.csv',
                  './../csv/passengers_100_2_50_5/averageOccupancy.csv', './../csv/passengers_100_2_15_70_10/averageOccupancy.csv']

    dataframes = []
    for file_path in file_paths:
        df = pd.read_csv(file_path)

        # Add a column to label which file the data came from
        label = os.path.splitext(file_path)[0]  # e.g., "occupancy_data_10"
        df['source'] = label
        dataframes.append(df)

    # Combine all into one DataFrame
    all_data = pd.concat(dataframes, ignore_index=True)

    # Create boxplot
    plt.figure(figsize=(10, 6))
    sns.boxplot(data=all_data, x='source', y='avg_occupancy')

    plt.title('Average Occupancy Distribution Across Datasets')
    plt.xlabel('Dataset')
    plt.ylabel('Average Occupancy')
    plt.xticks(rotation=15)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    show_data()
