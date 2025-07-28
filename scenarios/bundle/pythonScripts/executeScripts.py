import setupTables, extractBusstops, extractPersons, extractCabs, sys, os

SHOULD_RESET_TABLES = True # Change this to False if you want to recreate all tables

def print_separation_line():
    print('=' * 40)

def main(scenario_name: str):
    setupTables.main(SHOULD_RESET_TABLES)
    print_separation_line()
    extractPersons.main(scenario_name)
    print_separation_line()
    extractBusstops.main(scenario_name)
    print_separation_line()
    extractCabs.main(scenario_name)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        main(sys.argv[1])
