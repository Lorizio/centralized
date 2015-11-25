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
	private int maxIteration = 1000;

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

		A = SelectInitialSolution(vehicles, tasks);

		while((iter < maxIteration) && (System.currentTimeMillis() - time_start < timeout_plan)) {
			Aold = A;
			N = ChooseNeighbours(Aold,tasks);
			A = LocalChoice(N, tasks, Aold);
			iter++;
			System.out.println("Etape" + iter);
		}

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("The plan was generated in " + duration + " milliseconds.");
		return A.toPlans(vehicles, tasks);
	}


	/** Choose Neighbors**/
	public List<Solution> ChooseNeighbours(Solution Aold, TaskSet tasks) {
		System.out.println("choose neighbours");

		List<Solution> neighbors = new ArrayList<Solution>();

		Object[] tasksArray =  tasks.toArray();
		Integer randomlyChosenVehicle = chooseRandomVehicle(Aold.firstTaskVehicles);
		Integer indexOfFirstTask = Aold.getFirstTaskVehicles(randomlyChosenVehicle);
		System.out.println("indexoffirsttask : " + indexOfFirstTask);
		System.out.println("random vehicle :" + randomlyChosenVehicle);
		System.out.println("random vehicle task :" + Aold.getFirstTaskVehicles(randomlyChosenVehicle));

		// Applying the changing vehicle operator
		for (int vehicleTo = 0; vehicleTo < agent.vehicles().size(); vehicleTo++) {
			if (vehicleTo != randomlyChosenVehicle) {
				if (((Task)tasksArray[indexOfFirstTask/2]).weight <= computeFreeSpaceBeginning(vehicleTo, Aold)) {
					
					Solution A = Aold.changingVehicle(randomlyChosenVehicle, vehicleTo);
					neighbors.add(A);
				}	
			}	
		}


		//System.out.println("avant boucle length");

		// Applying the changing task order operator
		int length = 0;
		indexOfFirstTask = Aold.getFirstTaskVehicles(randomlyChosenVehicle);	
		while (indexOfFirstTask != null) {
			length++;
			indexOfFirstTask = Aold.getNextAction(indexOfFirstTask);
		}

		//System.out.println("taille chemin :"+length);
		if (length > 3) {
			// start at 1 to leave the first task untouched
			for (int tIdx1 = 1; tIdx1 < length-1; tIdx1++) {
				for (int tIdx2 = tIdx1 + 1; tIdx2 < length; tIdx2++) {
					Solution A = Aold.changingTaskOrder(randomlyChosenVehicle, tIdx1, tIdx2);
					if ((A != null) && isValid(A)) {
						neighbors.add(A);
					}	
				}
			}
		}
		return neighbors;
	}

	/** Local Choice **/
	public Solution LocalChoice(List<Solution> N, TaskSet tasks, Solution Aold) {
		System.out.println("Local choice");
		if (!N.isEmpty()) {
			Solution A = N.get(0);
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

	public int computeFreeSpaceBeginning(Integer vj, Solution A) {
		//System.out.println("compute free space");
		Integer Idx = A.getFirstTaskVehicles(vj);

		int weight = 0;

		// while the consecutive actions are 'PICKUP'
		while ((Idx != null) && (Idx % 2 == 0)) {
			weight = weight + allTasksList.get(Idx / 2).weight;
			Idx = A.nextAction[Idx];
			System.out.println("weight : " +weight);
		}
		return agent.vehicles().get(vj).capacity()-weight;
	}


	public boolean isValid(Solution A) {
		//System.out.println("isValid");

		boolean isValid = true;

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
		Object[] tasksArray = tasks.toArray();
		float cost = 0;
		// Compute the cost of a plan for each vehicle
		for( int vi = 0 ; vi <A.firstTaskVehicles.length; vi++ ) {
			int costPerKm = agent.vehicles().get(vi).costPerKm();
			if (A.firstTaskVehicles[vi] != null){
				City taskOneCity = ((Task)tasksArray[A.firstTaskVehicles[vi]/2]).pickupCity;
				double distHomeTaskOne = agent.vehicles().get(vi).homeCity().distanceTo(taskOneCity);
				cost += costPerKm * distHomeTaskOne;
			}

		}
		//all pickup
		for (int pi = 0 ; pi < A.nextAction.length; pi= pi+2) {
			City piCity = ((Task)tasksArray[pi/2]).pickupCity;
			City nextCity = null;
			int costPerKm = agent.vehicles().get(A.vehicles[pi/2]).costPerKm();
			Integer nextTaskTi = A.nextAction[pi];
			if (nextTaskTi != null) 
			{
				if (nextTaskTi % 2 == 0 )
					nextCity = ((Task)tasksArray[nextTaskTi/2]).pickupCity;
				else 
					nextCity = ((Task)tasksArray[nextTaskTi/2]).deliveryCity;
				cost += costPerKm * piCity.distanceTo(nextCity);
			}

		}
		//all delivered
		for (int di = 1 ; di < A.nextAction.length; di= di+2) {
			City diCity = ((Task)tasksArray[di/2]).deliveryCity;
			City nextCity = null;
			int costPerKm = agent.vehicles().get(A.vehicles[di/2]).costPerKm();
			Integer nextTaskTi = A.nextAction[di];
			if (nextTaskTi != null) {
				if (nextTaskTi % 2 == 0 )
					nextCity = ((Task)tasksArray[nextTaskTi/2]).pickupCity;
				else 
					nextCity = ((Task)tasksArray[nextTaskTi/2]).deliveryCity;
				cost += costPerKm * diCity.distanceTo(nextCity);
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
			System.out.println("pas de vehicule possible");
			System.err.println("Error when choosing random vehicle in ChooseNeighbor method.");
		}
		System.out.println("vehicles possible : " + possibleVehicleIdx);
		return vi;
	}
}
