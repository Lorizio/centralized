package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import cern.jet.random.PoissonSlow;
import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;


@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private long timeout_setup;
	private long timeout_plan;
	private List<Task> allTasksList;
	private List<Vehicle> vehicles;

	private Solution A; //current solution

	private final double p = 0.4;
	private final double p2 = 0.3;

	private final int maxIteration = 1000;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config\\settings_default.xml");
		}
		catch (Exception exc) {
			System.out.println("There was a problem loading the configuration file.");
		}

		// the setup method cannot last more than timeout_setup milliseconds
		timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
		// the plan method cannot execute more than timeout_plan milliseconds
		timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.vehicles = agent.vehicles();
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		long time_start = System.currentTimeMillis();
		this.allTasksList = new ArrayList<Task>();
		for (Task t : tasks) {
			allTasksList.add(t);
		}

		List<Solution> N;
		int iteration = 0;
		Solution A = SelectInitialSolution_2();

		while((iteration < maxIteration) && (System.currentTimeMillis() - time_start < timeout_plan)) {
			if (new Random().nextDouble() < p) {
				N = ChooseNeighbours(A, tasks);
				A = LocalChoice(N, tasks, A, p2);
			}
			iteration++;
			System.out.println("Etape n°" + iteration + " Current plan cost " + A.totalCost() + ".");
		}

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("----------------------------------------------------------");
		System.out.println("The plan was generated in " + duration + " milliseconds.");
		System.out.println("The plan cost " + A.totalCost() + ".");
		System.out.println("----------------------------------------------------------");
		return A.toPlans(true);
	}

	/** Choose Neighbors**/
	public List<Solution> ChooseNeighbours(Solution Aold, TaskSet tasks) {

		List<Solution> neighbors = new ArrayList<Solution>();
		//neighbors.add(Aold);

		Object[] tasksArray =  tasks.toArray();
		// Vehicle vehicleFrom = chooseRandomVehicle(Aold.firstTaskVehicles);
		
		// Applying the changing vehicle operator
		for (Vehicle vehicleTo : vehicles) {
						
			for(Vehicle vehicleFrom : vehicles) {
				
				Integer indexOfFirstTask = Aold.getFirstTaskVehicles(vehicleFrom.id());
				if (vehicleTo.id() != vehicleFrom.id()) {
					if (((Task)tasksArray[indexOfFirstTask/2]).weight <= computeFreeSpaceBeginning(vehicleTo.id(), Aold)) {
						Solution A = Aold.changingVehicle(vehicleFrom.id(), vehicleTo.id());
						neighbors.addAll(changingTaskOrder(A, vehicleFrom.id()));
						neighbors.add(A);
					}	
				}
				neighbors.addAll(changingTaskOrder(Aold, vehicleFrom.id()));
			}
		}
		return neighbors;
	}

	/** Changing task order**/
	public List <Solution> changingTaskOrder(Solution A, Integer vehicle) {
		List<Solution> neighbors = new ArrayList<Solution>();
		int length = 0;
		Integer IdxFirstTask = A.getFirstTaskVehicles(vehicle);	
		
		while (IdxFirstTask != null) {
			length++;
			IdxFirstTask = A.getNextAction(IdxFirstTask);
		}

		if (length > 3) {
			// start at 1 to leave the first task untouched
			for (int tIdx1 = 0; tIdx1 < length-1; tIdx1++) {
				for (int tIdx2 = tIdx1 + 1; tIdx2 < length; tIdx2++) {
					Solution newSol = A.changingTaskOrder(vehicle, tIdx1, tIdx2);
					if (isValid(newSol)) neighbors.add(newSol);
				}
			}
		}
		return neighbors;
	}

	/** Local Choice **/
	public Solution LocalChoice(List<Solution> N, TaskSet tasks, Solution Aold, double p) {
		//System.out.println("Local choice");
		if (!N.isEmpty()) {
			Solution A = N.get(0);
			
			// Find the solution with the minimal cost
			for(Solution s : N)
			{
				if (s.totalCost() < A.totalCost()) {
					A = s; 
				}
			}

			if (new Random().nextDouble() < p)
				return A;
			else return Aold;
		} else {
			System.err.println("Cannot start the SLS, no valid initial solution exists");
			return null;
		}
	}

	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity)) {
				plan.appendMove(city);
			}

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path()) {
				plan.appendMove(city);
			}

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}

	/** Select Initial Solution : All tasks in one vehicle **/
	public Solution SelectInitialSolution() {
		Solution initial = new Solution(vehicles, allTasksList);
		int Nt = allTasksList.size();
		Vehicle selectVehicle = vehicles.get(0);
		// Get the vehicle with the max capacity
		for(Vehicle v : vehicles) {
			if(v.capacity()>selectVehicle.capacity()) {
				selectVehicle = v;
			}
		}
		// Fill the tables with Naive plan
		// NextTaskActions & time
		for(int i = 0; i<(2*Nt-1); i++) {
			initial.nextAction[i] = i+1;
			initial.time[selectVehicle.id()][i] = i+1;
		}
		initial.nextAction[2*Nt-1] = null;
		initial.time[selectVehicle.id()][2*Nt-1] = 2*Nt;
		// NextTaskVehicles
		for(int vi = 0; vi<vehicles.size(); vi++) {
			initial.firstTaskVehicles[vi] = null;
		}
		initial.firstTaskVehicles[selectVehicle.id()] = 0;
		// Vehicles
		for(int ti = 0 ; ti < allTasksList.size(); ti++) {
			initial.vehicles[ti] = selectVehicle.id();
		}
		return initial;
	}

	/** Select Initial Solution 3 : All tasks in two vehicles **/
	public Solution SelectInitialSolution_3() {
		Solution initial = new Solution(vehicles, allTasksList);
		int Nt = allTasksList.size();
		Vehicle selectVehicle = vehicles.get(0);
		Vehicle selectVehicle2 = vehicles.get(1);

		// Fill the tables with Naive plan
		// NextTaskActions & time
		for(int i = 0; i<(Nt-1); i++) {
			initial.nextAction[i] = i+1;
			initial.time[selectVehicle.id()][i] = i+1;
		}
		initial.nextAction[Nt-1] = null;
		initial.time[selectVehicle.id()][Nt-1] = Nt;
		// --
		for(int i = Nt; i<(2*Nt-1); i++) {
			initial.nextAction[i] = i+1;
			initial.time[selectVehicle2.id()][i] = i+1;
		}
		initial.nextAction[2*Nt-1] = null;
		initial.time[selectVehicle2.id()][2*Nt-1] = 2*Nt;
		// NextTaskVehicles
		for(int vi = 0; vi<vehicles.size(); vi++) {
			initial.firstTaskVehicles[vi] = null;
		}
		initial.firstTaskVehicles[selectVehicle.id()] = 0;
		initial.firstTaskVehicles[selectVehicle2.id()] = Nt;
		// Vehicles
		for(int ti = 0 ; ti < Nt/2; ti++) {
			initial.vehicles[ti] = selectVehicle.id();
		}
		for (int tj = Nt/2; tj < Nt; tj++) {
			initial.vehicles[tj] = selectVehicle2.id();
		}
		return initial;
	}

	/** Select Initial Solution 2 : Pickup the tasks in the home cities and random attribute **/
	public Solution SelectInitialSolution_2() {
		Solution initial = new Solution(vehicles, allTasksList);

		ArrayList<Task> temp = new ArrayList<Task>(allTasksList);

		// Pickup the tasks in the home cities of the vehicles
		for (Vehicle v : vehicles) {
			for (Task t : allTasksList) {
				if ((t.pickupCity.equals(v.homeCity())) && (computeFreeSpaceBeginning(v.id(), initial)>=t.weight) ) {
					Integer IdxP = t.id*2;
					Integer NextIdxV = initial.firstTaskVehicles[v.id()];
					initial.nextAction[IdxP+1] = NextIdxV;
					initial.firstTaskVehicles[v.id()] = IdxP;
					initial.nextAction[IdxP] = IdxP + 1;
					initial.vehicles[t.id] = v.id();
					initial.updateTime(v.id());
					temp.remove(t);
				}
			}
		}

		// For the rest of the tasks assign randomly to a vehicle
		while (!temp.isEmpty()) {
			Task t = temp.get(0);
			int randomV = new Random().nextInt(vehicles.size());
			while (computeFreeSpaceBeginning(randomV, initial)>=t.weight && !temp.isEmpty()) {
				Integer IdxP = t.id*2;
				Integer NextIdxV = initial.firstTaskVehicles[randomV];
				initial.nextAction[IdxP+1] = NextIdxV;
				initial.firstTaskVehicles[randomV] = IdxP;
				initial.nextAction[IdxP] = IdxP + 1;
				initial.vehicles[t.id] = randomV;
				initial.updateTime(randomV);
				temp.remove(t);
				if (!temp.isEmpty()) t = temp.get(0);
			}
		}
		return initial;
	}

	/** Compute the free space by count the pickup at the beginning of a plan for a vehicle **/
	public int computeFreeSpaceBeginning(Integer vehicle, Solution A) {
		//System.out.println("compute free space");
		Integer Idx = A.getFirstTaskVehicles(vehicle);
		int weight = 0;

		// while the consecutive actions are 'PICKUP'
		while ((Idx != null) && (Idx % 2 == 0)) {
			weight = weight + allTasksList.get(Idx / 2).weight;
			Idx = A.nextAction[Idx];
			//System.out.println("weight : " +weight);
		}
		
		return agent.vehicles().get(vehicle).capacity()-weight;
	}

	/** Return true if the solution is a valid solution */
	public boolean isValid(Solution A) {
		//System.out.println("isValid");

		boolean isValid = true;
		if (A == null) return false;

		for (int vehicleID = 0; vehicleID < agent.vehicles().size(); vehicleID++) {
			Integer Idx = A.getFirstTaskVehicles(vehicleID);
			int weight = 0;
			int capacity = agent.vehicles().get(vehicleID).capacity();

			while (Idx != null) {
				if (Idx % 2 == 0) {
					weight = weight + allTasksList.get(Idx / 2).weight;
					if (weight > capacity) {
						isValid = false;
						break;
					}
				} else {
					weight = weight - allTasksList.get(Idx / 2).weight;
					if (weight < 0) {
						isValid = false;
						break;
					}
				}

				Idx = A.nextAction[Idx];
			}
		}
		return isValid;
	}

	/** Return a random vehicle which has tasks  **/
	private Vehicle chooseRandomVehicle(Integer[] nextTaskVehicle) {

		Vehicle vi = null;
		List<Vehicle> possibleVehicles = new ArrayList<Vehicle>();

		for (Vehicle v : vehicles) {
//		for (int i = 0; i < nextTaskVehicle.length; i++) {
			if (nextTaskVehicle[v.id()] != null) {
				possibleVehicles.add(v);
				//System.out.println(nextTaskVehicle[i] +" " +  i);
			}
		}
		if (!possibleVehicles.isEmpty()) {
			Random randomizer = new Random();
			vi = possibleVehicles.get(randomizer.nextInt(possibleVehicles.size()));
		} 
		else {
			//System.out.println("pas de vehicule possible");
			System.err.println("Error when choosing random vehicle in ChooseNeighbor method.");
		}
		//System.out.println("vehicles possible : " + possibleVehicleIdx);
		return vi;
	}
}
