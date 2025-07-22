import traci

traci.init(8765)
traci.setOrder(2)

while traci.simulation.getTime() < 1000:
    if traci.simulation.getTime() == 1:
        traci.route.add("my_python_route", ["9659058_75670639_268746441", "9659058_268746441_75666594", "9659083_75666594_75670644"])
    if traci.simulation.getTime() == 10:
        traci.vehicle.add("traci_vehicle", "my_python_route")
    traci.simulation.step()