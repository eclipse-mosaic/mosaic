import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

def show_data():
    custom_colors = {
        'paramset_1': '#66c2a5',
        'paramset_2': '#fc8d62',
        'paramset_3': '#8da0cb',
        'passengers_300': '#e78ac3',
        'passengers_500': '#42d7f5',
        'passengers_700': '#977af5',
    }
    folder_names= ['passengers_300', 'passengers_500', 'passengers_700', 'paramset_1', 'paramset_2', 'paramset_3']
    file_paths = []

    for folder_name in folder_names:
        file_paths.append(f'./../csv/{folder_name}/{file_name}')

    dataframes = []
    for file_path in file_paths:
        df = pd.read_csv(file_path)

        # Add a column to label which file the data came from
        label = str.split(os.path.splitext(file_path)[0], '/')[3]

        df['source'] = label
        dataframes.append(df)

    # Combine all into one DataFrame
    all_data = pd.concat(dataframes, ignore_index=True)

    # Create boxplot
    plt.figure(figsize=(10, 6))
    sns.boxplot(data=all_data, x='source', y=boxplot_y_label, hue='source', palette=custom_colors,
                notch=False, medianprops={"color": "r", "linewidth": 2}, legend=False)

    plt.grid(axis='y', linestyle='--', linewidth=0.7, alpha=0.7)
    plt.title(plot_title)
    plt.xlabel('Scenario dataset')
    plt.ylabel(plot_ylabel)
    plt.ylim(plot_ylim)
    plt.xticks(rotation=10)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    plot_ylabel= 'Waiting Time In Seconds'
    file_name = 'waitingTime.csv'
    boxplot_y_label = 'wait_time_seconds'
    plot_title = f'{plot_ylabel} Across Datasets'
    plot_ylim = 0, 1000
    show_data()
