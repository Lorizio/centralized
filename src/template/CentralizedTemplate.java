package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedTemplate implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
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
        long time_start = System.currentTimeMillis();
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
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
    
	public Solution searchSolutionSLS(COP constraints, List<Vehicle> vehicles, TaskSet tasks ) {
		Solution Aold;
		List<Solution> N;
		int f = constraints.f;
		Solution A;
		int maxStep = 0;
		
		A = SelectInitialSolution(vehicles, tasks);
		
		while(maxStep <= 100) {
			Aold = A;
			N = ChooseNeighbours(Aold,constraints);
			A = LocalChoice(N,f);
			maxStep++;
		}
		return A;
	}
	
	public Solution SelectInitialSolution(List<Vehicle> vehicles, TaskSet tasks ) {
		Solution initial = new Solution(vehicles.size(), tasks.size());
		int Nt = tasks.size();
		Vehicle selectVehicle = vehicles.get(0);
		// Get the vehicle with the max capacity
		for(Vehicle v : vehicles) {
			if(v.capacity()>selectVehicle.capacity()) 
				selectVehicle = v;
		}
		// Fill the tables with Naive plan
		// NextTaskActions & time
		for(int i = 0; i<(2*Nt-1); i++) {
			initial.nextTaskActions[i] = i+1;
			initial.time[selectVehicle.id()][i] = i+1;
		}
		initial.nextTaskActions[2*Nt-1] = null;
		initial.time[selectVehicle.id()][2*Nt-1] = 2*Nt;
		// NextTaskVehicles
		for(int vi = 0; vi<vehicles.size();vi++) {
			initial.nextTaskVehicles[vi] = null;
		}
		initial.nextTaskVehicles[selectVehicle.id()] = 0;
		// Vehicles
		for(int ti = 0 ; ti < tasks.size(); ti++) {
			initial.vehicles[ti] = selectVehicle.id();
		}
		return initial;
	}
	
	public List<Solution> ChooseNeighbours(Solution Aold, COP constraints, TaskSet tasks) {
		List<Solution> N = new ArrayList<Solution>();

		Integer[] nextTV = Aold.nextTaskVehicles;
		int vIndex = chooseRandomVehicle(nextTV);
		Integer pickFirstTaskIndex = 0; // t

		for (int j = 0; j < agent.vehicles().size(); j++) {
			if (j != vIndex) {
				pickFirstTaskIndex = Aold.nextTaskVehicles[vIndex];
				
				Task[] tasksArray = (Task[]) tasks.toArray();
				if (tasksArray[pickFirstTaskIndex].weight <= agent.vehicles().get(vIndex).capacity()) {
					Solution A = ChangingVehicle(Aold, vIndex, j);
					N.add(A);
				}
				
			}
		}
		
		int length = 0;
		pickFirstTaskIndex = vIndex;
		
		do {
			pickFirstTaskIndex = Aold.nextTaskActions[pickFirstTaskIndex];
			length++;
		} while (pickFirstTaskIndex != null);
		
		
		if (length > 2) {
			for (int tIdx1 = 0; tIdx1 < length-1; tIdx1++) {
				for (int tIdx2 = tIdx1 + 1; tIdx2 < length; tIdx2++) {
					Solution A = ChangingTaskOrder(Aold, vIndex, tIdx1, tIdx2);
					if (A != null) {
						N.add(A);
					}
					
				}
			}
		}

		return N;
	}
	
	public Solution LocalChoice(List<Solution> N, TaskSet tasks) {
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
		return A;
	}
	
	public float computeCost(Solution A, TaskSet tasks) {
		Object[] tasksArray = tasks.toArray();
		float cost = 0;
		// Compute the cost of a plan for each vehicle
		for( int vi = 0 ; vi < A.vehicles.length; vi ++ ) {
			int costPerKm = agent.vehicles().get(vi).costPerKm();
			City taskOneCity = ((Task)tasksArray[A.nextTaskVehicles[vi]/2]).pickupCity;
			double distHomeTaskOne = agent.vehicles().get(vi).homeCity().distanceTo(taskOneCity);
			cost += costPerKm * distHomeTaskOne;
		}
		//all pickup
		for (int pi = 0 ; pi < A.nextTaskActions.length; pi= pi+2) {
			City piCity = ((Task)tasksArray[pi/2]).pickupCity;
			City nextCity = null;
			int costPerKm = agent.vehicles().get(A.vehicles[pi]).costPerKm();
			int nextTaskTi = A.nextTaskActions[pi];
			if (nextTaskTi % 2 == 0 )
				nextCity = ((Task)tasksArray[A.nextTaskVehicles[nextTaskTi]/2]).pickupCity;
			else 
				nextCity = ((Task)tasksArray[A.nextTaskVehicles[nextTaskTi]/2]).deliveryCity;
			cost += costPerKm * piCity.distanceTo(nextCity);
		}
		//all delivered
		for (int di = 1 ; di < A.nextTaskActions.length; di= di+2) {
			City diCity = ((Task)tasksArray[di/2]).deliveryCity;
			City nextCity = null;
			int costPerKm = agent.vehicles().get(A.vehicles[di]).costPerKm();
			int nextTaskTi = A.nextTaskActions[di];
			if (nextTaskTi % 2 == 0 )
				nextCity = ((Task)tasksArray[A.nextTaskVehicles[nextTaskTi]/2]).pickupCity;
			else 
				nextCity = ((Task)tasksArray[A.nextTaskVehicles[nextTaskTi]/2]).deliveryCity;
			cost += costPerKm * diCity.distanceTo(nextCity);
		}
		return cost;
	}
	
	/** Changing the vehicle for a task (pickup and delivery) **/
	public Solution ChangingVehicle(Solution A, int v1, int v2) {
		Solution A1 = A;
		int t = A.getNextTaskVehicles(v1);	
		// If it is a deliver only change t
		if (t % 2 == 0) {
			A1.setNextTaskVehicles(v1, A1.getNextTaskActions(t));
			A1.setNextTaskActions(t, A1.getNextTaskVehicles(v2));
			A1.setNextTaskVehicles(v2, t);
		}
		else {
			// We have to assign the pickup t and the deliver to the same vehicle
			if (A1.getNextTaskActions(t) == t+1)
				A1.setNextTaskVehicles(v1, A1.getNextTaskActions(t+1));
			else
				A1.setNextTaskVehicles(v1, A1.getNextTaskActions(t));
			A1.setNextTaskActions(t, t+1);
			A1.setNextTaskVehicles(v2, t);
		}
		UpdateTime(A1, v1);
		UpdateTime(A1, v2);
		return A1;
	}
	
	/** Changing task order **/
	public Solution ChangingTaskOrder(Solution A, int vi, int tIdx1, int tIdx2) {
		Solution A1 = A;
		
		int tPre1 = 0;// previous task of action1
		int t1 = A1.getNextTaskVehicles(vi); // task1
		int count = 1;
		while(count != tIdx1) {
			tPre1 = t1;
			t1 = A1.getNextTaskActions(t1);
			count ++;
		}
		int tPost1 = A1.getNextTaskActions(t1); // the task done after t1
		int tPre2 = t1; //previous task of task2
		int t2 = A1.getNextTaskActions(tPre2); //task2
		count ++;
		
		while(count < tIdx2) {
			tPre2 = t2;
			t2 = A1.getNextTaskActions(t2);
			count ++;
		}
		int tPost2 = A1.getNextTaskActions(t2); // the task done after t2
		
		// Check if the echange is possible : not put a deliver before its pickup and the opposite
		// t1 is a pickup
		if (t1 % 2 == 0) {
			// The deliver is before the task2
			if (A1.time[vi][t1]<=A1.time[vi][t2]) {
				return null;
			}
		}
		// t2 is a deliver
		if (t2 % 2 != 0) {
			// The pickup is after t1
			if(A1.time[vi][t2-1]>=A1.time[vi][t1]){
				return null;
			}
		}
		
		// Exchanging two tasks
		if (tPost1 == t2) {
			// t2 is done just after t1
			A1.setNextTaskActions(tPre1, t2);
			A1.setNextTaskActions(t2, t1);
			A1.setNextTaskActions(t1, tPost2);
		}
		else {
			A1.setNextTaskActions(tPre1, t2);
			A1.setNextTaskActions(tPre2, t1);
			A1.setNextTaskActions(t2, tPost1);
			A1.setNextTaskActions(t1, tPost2);
		}
		UpdateTime(A1, vi);
		return A1;
	}
	
	public void UpdateTime(Solution A, int vi) {
		Arrays.fill(A.time, 0);
		if (A.getNextTaskVehicles(vi) != null) {
			int ti = A.getNextTaskVehicles(vi);
			A.time[vi][ti] = 1;
			Integer tj = A.getNextTaskActions(ti);
			while (tj != null) {
				tj = A.getNextTaskActions(ti);
				if (tj != null) {
					A.time[vi][tj] = A.time[vi][ti]+1;
					ti = tj;
				}
			}
		}
	}
	
	
	private int chooseRandomVehicle(Integer[] nTV) {
		int vehicleId = 0;

		List<Integer> possibleVehicleIndexes = new ArrayList<Integer>();

		if (!possibleVehicleIndexes.isEmpty()) {

			for (int i = 0; i < nTV.length; i++) {
				if (nTV[i] != null) {
					possibleVehicleIndexes.add(i);
				}
			}

			Random randomizer = new Random();
			vehicleId = possibleVehicleIndexes.get(randomizer.nextInt(possibleVehicleIndexes.size()));
		} else {
			System.err.println("Error when choosing random vehicle in ChooseNeighbor method.");
		}


		return vehicleId;
	}
}
