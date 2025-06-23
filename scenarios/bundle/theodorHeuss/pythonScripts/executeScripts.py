import setupTables, extractBusstops, extractPersons, extractCabs

SHOULD_RESET_TABLES = True # Change this to False if you want to recreate all tables

def print_separation_line():
    print('=' * 40)

def main():
    setupTables.main(SHOULD_RESET_TABLES)
    print_separation_line()
    extractPersons.main()
    print_separation_line()
    extractBusstops.main()
    print_separation_line()
    extractCabs.main()

if __name__ == "__main__":
    main()
