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

	private double p = 0.5;
	private int maxIteration = 5000;

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

	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		this.allTasksList = new ArrayList<Task>();
		for (Task t : tasks) {
			allTasksList.add(t);
		}

		long time_start = System.currentTimeMillis();

		Solution Aold;
		List<Solution> N;
		Solution A;
		int iter = 0;

		A = SelectInitialSolution_3(vehicles, tasks);

		while((iter < maxIteration) && (System.currentTimeMillis() - time_start < timeout_plan)) {
			Aold = A;
			N = ChooseNeighbours(Aold,tasks);
			A = LocalChoice(N, tasks, Aold,p);
			iter++;
			System.out.println("Etape n°" + iter);
		}

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("The plan was generated in " + duration + " milliseconds.");
//		for (int i = 0; i < A.nextAction.length; i++) {
//			System.out.println(A.nextAction[i]);
//		}
		return A.toPlans(vehicles, tasks,true);
	}

	/** Choose Neighbors**/
	public List<Solution> ChooseNeighbours(Solution Aold, TaskSet tasks) {
		//System.out.println("choose neighbours");

		List<Solution> neighbors = new ArrayList<Solution>();

		Object[] tasksArray =  tasks.toArray();
		Integer randomlyChosenVehicle = chooseRandomVehicle(Aold.firstTaskVehicles);
		Integer indexOfFirstTask = Aold.getFirstTaskVehicles(randomlyChosenVehicle);

		// Applying the changing vehicle operator
		for (int vehicleTo = 0; vehicleTo < agent.vehicles().size()-1; vehicleTo++) {
			for (int vehicleFrom = 1; vehicleFrom<agent.vehicles().size();vehicleFrom++ )
			{
				if (vehicleTo != vehicleFrom) {
					if (((Task)tasksArray[indexOfFirstTask/2]).weight <= computeFreeSpaceBeginning(vehicleTo, Aold)) {
						//System.out.println("changing vehicle");
						Solution A = Aold.changingVehicle(vehicleFrom, vehicleTo);
						neighbors.addAll(changingTaskOrder(A, vehicleFrom));
						//System.out.println("cost changing vehicle "+ computeCost(A, tasks));
						neighbors.add(A);
					}	
				}
			}
			
		}

		

		//Choose the solution with the min cost
		if (neighbors == null) System.out.println("N null");
		if (Aold == null) System.out.println("Aold null");
		
		//Solution Amin = LocalChoice(neighbors, tasks, Aold, 1);
		//if (Amin == null) System.out.println("Amin null");
		
		neighbors.addAll(changingTaskOrder(Aold, randomlyChosenVehicle));
		//neighbors.addAll(changingTaskOrder(Amin, randomlyChosenVehicle));
		//System.out.println("Before return neighboors");
		return neighbors;
	}

	/** changing task order**/
	public List <Solution> changingTaskOrder(Solution A, Integer vehicle) {
		List<Solution> neighbors = new ArrayList<Solution>();
		// Applying the changing task order operator
		int length = 0;
		Integer indexOfFirstTask = A.getFirstTaskVehicles(vehicle);	
		while (indexOfFirstTask != null) {
			length++;
			indexOfFirstTask = A.getNextAction(indexOfFirstTask);
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
			if (A == null)	System.out.println("plan null");
			float minCost = computeCost(A, tasks);	
			// Find the solution with the minimal cost
			for(Solution s : N)
			{
				float f = computeCost(s,tasks);
				if (f < minCost) {
					minCost = f;
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

	/** Select Initial Solution **/
	public Solution SelectInitialSolution(List<Vehicle> vehicles, TaskSet tasks ) {
		Solution initial = new Solution(vehicles.size(), tasks.size());
		int Nt = tasks.size();
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
		for(int ti = 0 ; ti < tasks.size(); ti++) {
			initial.vehicles[ti] = selectVehicle.id();
		}
		return initial;
	}
	
	/** Select Initial Solution **/
	public Solution SelectInitialSolution_3(List<Vehicle> vehicles, TaskSet tasks ) {
		Solution initial = new Solution(vehicles.size(), tasks.size());
		int Nt = tasks.size();
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

	public Solution SelectInitialSolution_2(List<Vehicle> vehicles, TaskSet tasks) {
		Solution initial = new Solution(vehicles.size(), tasks.size());
		
		ArrayList<Task> temp = new ArrayList<Task>(allTasksList);
		
		// Pickup the tasks in the home cities of the vehicles
		for (Vehicle v : vehicles) {
			for (Task t : tasks) {
				if ((t.pickupCity.equals(v.homeCity())) && (computeFreeSpaceBeginning(v.id(), initial) >= t.weight) ) {
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
			if (computeFreeSpaceBeginning(randomV, initial) > t.weight) {
				Integer IdxP = t.id*2;
				Integer NextIdxV = initial.firstTaskVehicles[randomV];
				initial.nextAction[IdxP+1] = NextIdxV;
				initial.firstTaskVehicles[randomV] = IdxP;
				initial.nextAction[IdxP] = IdxP + 1;
				initial.vehicles[t.id] = randomV;
				initial.updateTime(randomV);
				temp.remove(t);
			}
		}
		return initial;
	}
	
	public int computeFreeSpaceBeginning(Integer vj, Solution A) {
		//System.out.println("compute free space");
		Integer Idx = A.getFirstTaskVehicles(vj);

		int weight = 0;

		// while the consecutive actions are 'PICKUP'
		while ((Idx != null) && (Idx % 2 == 0)) {
			weight = weight + allTasksList.get(Idx / 2).weight;
			Idx = A.nextAction[Idx];
			//System.out.println("weight : " +weight);
		}
		return agent.vehicles().get(vj).capacity()-weight;
	}

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

	/** Compute Cost **/
	public float computeCost(Solution A, TaskSet tasks) {
//		Object[] tasksArray = tasks.toArray();
		float cost = 0;
//		// Compute the cost of a plan for each vehicle
//		for( int vi = 0 ; vi <A.firstTaskVehicles.length; vi++ ) {
//			int costPerKm = agent.vehicles().get(vi).costPerKm();
//			if (A.firstTaskVehicles[vi] != null){
//				City taskOneCity = ((Task)tasksArray[A.firstTaskVehicles[vi]/2]).pickupCity;
//				double distHomeTaskOne = agent.vehicles().get(vi).homeCity().distanceTo(taskOneCity);
//				cost += costPerKm * distHomeTaskOne;
//			}
//
//		}
//		//all pickup
//		for (int pi = 0 ; pi<A.nextAction.length; pi= pi+2) {
//			City piCity = ((Task)tasksArray[pi/2]).pickupCity;
//			City nextCity = null;
//			int costPerKm = agent.vehicles().get(A.vehicles[pi/2]).costPerKm();
//			Integer nextTaskTi = A.nextAction[pi];
//			if (nextTaskTi != null) 
//			{
//				if (nextTaskTi % 2 == 0 )
//					nextCity = ((Task)tasksArray[nextTaskTi/2]).pickupCity;
//				else 
//					nextCity = ((Task)tasksArray[nextTaskTi/2]).deliveryCity;
//				cost += costPerKm * piCity.distanceTo(nextCity);
//			}
//
//		}
//		//all delivered
//		for (int di = 1 ; di<A.nextAction.length; di=di+2) {
//			City diCity = ((Task)tasksArray[di/2]).deliveryCity;
//			City nextCity = null;
//			int costPerKm = agent.vehicles().get(A.vehicles[di/2]).costPerKm();
//			Integer nextTaskTi = A.nextAction[di];
//			if (nextTaskTi != null) {
//				if (nextTaskTi % 2 == 0 )
//					nextCity = ((Task)tasksArray[nextTaskTi/2]).pickupCity;
//				else 
//					nextCity = ((Task)tasksArray[nextTaskTi/2]).deliveryCity;
//				cost += costPerKm * diCity.distanceTo(nextCity);
//			}
//		}
		List<Plan> p = A.toPlans(agent.vehicles(), allTasksList,false);
		for (int i=0;i<p.size();i++){
			if (p.get(i)!=null) {
				cost += p.get(i).totalDistance()*agent.vehicles().get(i).costPerKm();
			}
			
		}
		return cost;
	}


	/** Return a random vehicle which has tasks  **/
	private int chooseRandomVehicle(Integer[] nextTaskVehicle) {

		int vi = 0;
		List<Integer> possibleVehicleIdx = new ArrayList<Integer>();

		for (int i = 0; i < nextTaskVehicle.length; i++) {
			if (nextTaskVehicle[i] != null) {
				possibleVehicleIdx.add(i);
				//System.out.println(nextTaskVehicle[i] +" " +  i);
			}
		}
		if (!possibleVehicleIdx.isEmpty()) {
			Random randomizer = new Random();
			vi = possibleVehicleIdx.get(randomizer.nextInt(possibleVehicleIdx.size()));
		} 
		else {
			//System.out.println("pas de vehicule possible");
			System.err.println("Error when choosing random vehicle in ChooseNeighbor method.");
		}
		//System.out.println("vehicles possible : " + possibleVehicleIdx);
		return vi;
	}
}
