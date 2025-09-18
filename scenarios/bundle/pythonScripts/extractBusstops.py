import os, math, setupTables, sys
from lxml import etree
from mysql.connector.pooling import PooledMySQLConnection
from pyproj import Transformer, CRS

my_db_connection: PooledMySQLConnection

# === Helper to interpolate position along polyline ===
def get_point_along_shape(shape, distance):
    for i in range(len(shape) - 1):
        x0, y0 = shape[i]
        x1, y1 = shape[i + 1]
        seg_len = math.hypot(x1 - x0, y1 - y0)
        if distance <= seg_len:
            ratio = distance / seg_len
            x = x0 + ratio * (x1 - x0)
            y = y0 + ratio * (y1 - y0)
            return x, y
        distance -= seg_len
    return shape[-1]  # fallback to last point

# === Insert the bus stop into the DB ===
def add_bus_stop_to_db(stop, lat, lon):
    insert_values_query = ("INSERT INTO stop (bearing, latitude, longitude, name, type, capacity, sumo_edge) "
                           "VALUES (0, %s, %s, %s, NULL, %s, %s)")
    sumo_edge = stop.attrib["lane"]
    values = (lat, lon, stop.attrib["id"], stop.attrib["personCapacity"], sumo_edge[:len(sumo_edge) - 2])
    my_db_connection.cursor().execute(insert_values_query, values)
    my_db_connection.commit()

def main(scenario_name: str):
    print("Executing script:", os.path.basename(__file__))
    global my_db_connection
    # === Load projection and netOffset from net.xml ===
    net_file = "../{0}/sumo/{0}.net.xml".format(scenario_name)
    net_tree = etree.parse(net_file)
    location = net_tree.find("location")

    net_offset_x, net_offset_y = map(float, location.attrib["netOffset"].split(","))
    proj_string = location.attrib["projParameter"]
    # Set up transformer from UTM to WGS84
    utm33 = CRS.from_proj4(proj_string)
    wgs84 = CRS.from_epsg(4326)  # Standard lat/lon

    transformer = Transformer.from_crs(utm33, wgs84, always_xy=True)

    # === Parse lane shapes ===
    lane_shapes = {}
    for lane in net_tree.findall(".//lane"):
        lane_id = lane.attrib["id"]
        if "shape" in lane.attrib:
            shape = [
                (float(x), float(y))
                for x, y in (pt.split(",") for pt in lane.attrib["shape"].strip().split())
            ]
            lane_shapes[lane_id] = shape

    # === Parse bus stops and compute coordinates ===
    add_file = "../{0}/sumo/{0}.bus.add.xml".format(scenario_name)
    add_tree = etree.parse(add_file)
    bus_stops = add_tree.findall(".//busStop")

    setupTables.setup_db_connection()
    my_db_connection = setupTables.my_db_connection

    print("=== Bus Stop Coordinates ===")
    for stop in bus_stops:
        stop_id = stop.attrib["id"]
        lane_id = stop.attrib["lane"]
        start_pos = float(stop.attrib.get("startPos", 0))

        shape = lane_shapes.get(lane_id)
        if not shape:
            print(f"No shape for lane {lane_id}")
            continue

        x, y = get_point_along_shape(shape, start_pos)
        x_utm = x - net_offset_x
        y_utm = y - net_offset_y
        lon, lat = transformer.transform(x_utm, y_utm)

        print(f"ID: {stop_id} Lat/Lon: ({lat:.6f}, {lon:.6f})")
        add_bus_stop_to_db(stop, lat, lon)

    print("Bus stops inserted into the DB!")
    my_db_connection.close()

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Wrong number of input arguments. Input should be \"{} <SCENARIO_NAME>\"".format(os.path.basename(__file__)))

    try:
        float(sys.argv[1])
        print("Please provide a valid scenario name of type string!")
    except ValueError:
        main(sys.argv[1])
