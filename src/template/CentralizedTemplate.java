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
	
	public List<Solution> ChooseNeighbours(Solution Aold, COP constraints) {
		List<Solution> N = new ArrayList<Solution>();
		
		return N;
	}
	
	public Solution LocalChoice(List<Solution> N, int f) {
		Solution s = new Solution(0,0);
		
		
		return s;
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
	// ATTENTION VERSION NON OK POUR PLRS TACHE VERSION PAPIER
	public Solution ChangingTaskOrder(Solution A, int vi, int tIdx1, int tIdx2) {
		Solution A1 = A;
		int tPre1 = vi; // previous task of task1
		int t1 = A1.getNextTaskActions(tPre1); // task1
		int count = 1;
		while(count < tIdx1) {
			tPre1 = t1;
			t1 = A1.getNextTaskActions(t1);
			count ++;
		}
		int tPost1 = A1.getNextTaskActions(t1); // the task done after t1
		int tPre2 = t1; //previous task of task2
		int t2 = A1.getNextTaskActions(tPre2); //task2
		count++;
		while(count<tIdx2) {
			tPre2 = t2;
			t2 = A1.getNextTaskActions(t2);
			count ++;
		}
		int tPost2 = A1.getNextTaskActions(t2); // the task done after t1
		// exchanging two tasks
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
		if (A.getNextTaskVehicles(vi) != null) {
			int ti = A.getNextTaskVehicles(vi);
			A.time[ti] = 1;
			Integer tj = A.getNextTaskActions(ti);
			while (tj != null) {
				tj = A.getNextTaskActions(ti);
				if (tj != null) {
					A.time[tj] = A.time[ti]+1;
					ti = tj;
				}
			}
		}
	}
}
