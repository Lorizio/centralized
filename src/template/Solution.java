package template;

import java.util.ArrayList;
import java.util.List;

import logist.plan.Action;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class Solution {
	private List<Vehicle> vehiclesList;
	private List<Task> tasksList;

	private int numberOfVehicle = 0;
	private int numberOfTasks = 0;

	Integer[] nextAction;
	Integer[] firstTaskVehicles;
	Integer[][] time;
	Integer[] vehicles;

	public Solution(int Nv, int Nt) {
		this.numberOfVehicle = Nv;
		this.numberOfTasks = Nt;

		this.initialize();
	}

	public Solution (List<Vehicle> vehiclesL, List<Task> tasksL) {
		this.vehiclesList = vehiclesL;
		this.tasksList = tasksL;

		this.numberOfTasks = this.tasksList.size();
		this.numberOfVehicle = this.vehiclesList.size();

		initialize();

	}

	/**
	 * Creates a solution based on a previous solution (s) which contains
	 * another additional task (t)
	 * @param s
	 * @param tasks
	 */
	public Solution(Solution s, Task t) {
		this.numberOfVehicle = s.numberOfVehicle;
		this.numberOfTasks = s.numberOfTasks + 1;

		//		this.nextAction = new Integer[2*numberOfTasks];
		//		int i = 0;
		//		for (i = 0; i < s.nextAction.length; i++) {
		//			this.nextAction[]
		//		}


	}

	public Solution(List<Vehicle> vehiclesL) {
		this.vehiclesList = vehiclesL;
		this.tasksList = new ArrayList<Task>();

		this.numberOfVehicle = this.vehiclesList.size();
		this.numberOfTasks = this.tasksList.size();

		initialize();
	}

	public Solution(Integer[] nTA, Integer[] nTV, Integer[][] time, Integer[] v) {
		this.numberOfTasks = v.length;
		this.numberOfVehicle = nTV.length;

		this.nextAction = nTA;
		this.firstTaskVehicles = nTV;
		this.time = time;
		this.vehicles = v;

	}

	private void initialize() {
		this.nextAction = new Integer[2*numberOfTasks];
		this.firstTaskVehicles = new Integer[numberOfVehicle];
		this.time = new Integer[numberOfVehicle][2*numberOfTasks];
		this.vehicles = new Integer[numberOfTasks];
	}

	public Integer getNextAction(int index) {
		return nextAction[index];
	}

	public void setNextAction(int index, Integer newValue) {
		nextAction[index] = newValue;
	}

	public Integer getFirstTaskVehicles(int index) {
		return firstTaskVehicles[index];
	}

	public void setFirstTaskVehicles(int index, Integer newValue) {
		firstTaskVehicles[index] = newValue;
	}

	public Solution changingTaskOrder(int v, int tIdx1, int tIdx2) { //Integer preA1, Integer a1, Integer postA1, Integer preA2, Integer a2, Integer postA2) {

		Solution A1 = clone(this);
		//System.out.println("("+ tIdx1 +","+ tIdx2+")");
		Integer tPre1 = null; // previous task of action1
		Integer t1 = A1.getFirstTaskVehicles(v); // task1
		int count = 0;

		while(count < tIdx1) {
			tPre1 = t1;
			t1 = A1.getNextAction(t1);
			count++;
		}

		Integer tPost1 = A1.getNextAction(t1); // the task done after t1

		Integer tPre2 = t1; //previous task of task2
		Integer t2 = A1.getNextAction(tPre2); //task2
		count ++;
		while(count < tIdx2) {
			tPre2 = t2;
			t2 = A1.getNextAction(t2);
			count++;
		}
		//System.out.println(t2);
		Integer tPost2 = A1.getNextAction(t2); // the task done after t2

		// Check if the exchange is possible : not put a deliver before its pickup and the opposite

		if (((t1 % 2 == 0) && (A1.time[v][t2] >= A1.time[v][t1+1])) // t1 is a pickup
				||((t2 % 2 != 0) && (A1.time[v][t1] <= A1.time[v][t2-1]))) { // t2 is a deliver
			return null;
		}
		else {
			if (tPre1 != null) {
				A1.setNextAction(tPre1, t2);
				A1.setNextAction(t1, tPost2);
				// t2 is done just after t1
				if (tPost1 == t2)  {
					A1.setNextAction(t2, t1);
				}
				else {
					A1.setNextAction(tPre2, t1);
					A1.setNextAction(t2, tPost1);
				}

				//A1.updateTime(v);
			} else {
				A1.setFirstTaskVehicles(v, t2);
				A1.setNextAction(t1, tPost2);

				// t2 is done just after t1
				if (tPost1 == t2)  {
					A1.setNextAction(t2, t1);
				}
				else {
					A1.setNextAction(tPre2, t1);
					A1.setNextAction(t2, tPost1);
				}
			}

			updateTime(v);
			//			int timeT1 = A1.time[v][t1];
			//			A1.time[v][t1] = A1.time[v][t2];
			//			A1.time[v][t2] = timeT1;
			return A1;
		}
	}


	/**
	 * Find the next pickup action given an action.
	 * Return null if there exist no next pickup.
	 * @param action
	 * @return null if there exist no next pickup
	 */
	public Integer findNextPickup(Integer action) {
		Integer currentAction = action;

		do {
			currentAction = getNextAction(currentAction);
		} while ((currentAction != null) && (currentAction % 2 == 1));

		return currentAction;
	}

	private Integer post(Integer action) {
		return getNextAction(action);
	}


	private Integer pre(Integer targetAction, Integer fromAction) {
		Integer action = fromAction;
		Integer preAction = action;
		while ((action != targetAction) && (action != null)){
			preAction = action;
			action = post(action);
		}

		if (action == null) {
			System.err.println("pre() : fromAction et targetAction ne sont pas sur le mÃªme chemin");
		}

		return preAction;
	}


	/** Changing the vehicle for a task (pickup and delivery) **/
	public Solution changingVehicle(int vFrom, int vTo) {
		//System.out.println("Changing vehicle");

		Solution A1 = clone(this);

		Integer firstTaskPickup = A1.getFirstTaskVehicles(vFrom);
		//System.out.println("firstTaskpickup : " + firstTaskPickup);
		Integer postTaskPickup = A1.post(firstTaskPickup);

		Integer firstTaskDelivery = firstTaskPickup + 1;
		//System.out.println(firstTaskPickup);
		Integer preTaskDelivery = A1.pre(firstTaskDelivery, firstTaskPickup);
		Integer postTaskDelivery = A1.post(firstTaskDelivery);

		// put the task at the beginning
		//		if (postTaskPickup == firstTaskDelivery) { // D_i immediately follows P_i
		//			A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
		//			A1.setNextAction(firstTaskDelivery, A1.getFirstTaskVehicles(vTo));
		//			A1.setNextAction(firstTaskPickup, firstTaskDelivery);
		//			A1.setFirstTaskVehicles(vTo, firstTaskPickup);
		//		} else {
		//			A1.setNextAction(preTaskDelivery, postTaskDelivery);
		//			A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
		//			A1.setNextAction(firstTaskDelivery, A1.getFirstTaskVehicles(vTo));
		//			A1.setNextAction(firstTaskPickup, firstTaskDelivery);
		//			A1.setFirstTaskVehicles(vTo, firstTaskPickup);
		//		}

		// put the task at the end
		if (A1.firstTaskVehicles[vTo] != null) {
			if (postTaskPickup == firstTaskDelivery) { // D_i immediately follows P_i
				A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
				A1.setNextAction(firstTaskDelivery, null);
				A1.setNextAction(firstTaskPickup, firstTaskDelivery);
				A1.setNextAction(findLastAction(A1.firstTaskVehicles[vTo]), firstTaskPickup);
			} else {
				A1.setNextAction(preTaskDelivery, postTaskDelivery);
				A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
				A1.setNextAction(firstTaskDelivery, null);
				A1.setNextAction(firstTaskPickup, firstTaskDelivery);
				A1.setNextAction(findLastAction(A1.firstTaskVehicles[vTo]), firstTaskPickup);
			}
		} else {
			if (postTaskPickup == firstTaskDelivery) { // D_i immediately follows P_i
				A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
				A1.setNextAction(firstTaskDelivery, null);
				A1.setNextAction(firstTaskPickup, firstTaskDelivery);
				A1.setFirstTaskVehicles(vTo, firstTaskPickup);
			} else {
				A1.setNextAction(preTaskDelivery, postTaskDelivery);
				A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
				A1.setNextAction(firstTaskDelivery, null);
				A1.setNextAction(firstTaskPickup, firstTaskDelivery);
				A1.setFirstTaskVehicles(vTo, firstTaskPickup);
			}
		}

		A1.vehicles[firstTaskPickup/2] = vTo;
		A1.updateTime(vFrom);
		A1.updateTime(vTo);
		//System.out.println("end changingVehicle");
		//System.out.println("changing vehicle vFrom : "+vFrom+" vTo :"+vTo);
		return A1;

		/*
		// Pick up
		//else {
			// We have to assign the pickup t and the deliver to the same vehicle
			if (A1.getNextAction(firstTaskPickup) == firstTaskPickup+1) {
				A1.setFirstTaskVehicles(vFrom, A1.getNextAction(firstTaskPickup+1));
				A1.setNextAction(firstTaskPickup, firstTaskPickup+1);
				A1.setNextAction(firstTaskPickup+1, A1.getFirstTaskVehicles(vTo));
				A1.setFirstTaskVehicles(vTo, firstTaskPickup);
			}	
			else {
				Integer id = firstTaskPickup;
				int tPre = id;
				while (id != firstTaskPickup+1){
					tPre = id;
					id = A1.getNextAction(id);
				}
				A1.setNextAction(tPre, A1.nextAction[firstTaskPickup+1]);		
				A1.setFirstTaskVehicles(vFrom,A1.getNextAction(firstTaskPickup));
				A1.setNextAction(firstTaskPickup, firstTaskPickup+1);
				A1.setNextAction(firstTaskPickup+1, A1.getFirstTaskVehicles(vTo));
				A1.setFirstTaskVehicles(vTo,firstTaskPickup);
			}
			A1.vehicles[(firstTaskPickup+1)/2] = vTo;	
		//}
		A1.vehicles[firstTaskPickup/2] = vTo;
		A1.updateTime(vFrom);
		A1.updateTime(vTo);

		return A1;*/
	}

	private Integer findLastAction(Integer firstAction) {
		if (firstAction == null) {
			return null;
		} else {

			int lastAction = firstAction;
			int nextAction = firstAction;
			while (getNextAction(nextAction) != null) {
				lastAction = nextAction;
				nextAction = getNextAction(nextAction);
			}

			return nextAction;
		}
	}


	public Solution clone(Solution s) {
		Integer[][] newTime = new Integer[s.numberOfVehicle][2*s.numberOfTasks];
		for (int v = 0; v < s.numberOfVehicle; v++){
			newTime[v] = s.time[v].clone();
		}

		Solution newSolution = new Solution(
				s.nextAction.clone(), 
				s.firstTaskVehicles.clone(), 
				newTime, 
				s.vehicles.clone());

		newSolution.numberOfTasks = s.numberOfTasks;
		newSolution.numberOfVehicle = s.numberOfVehicle;
		newSolution.vehiclesList = new ArrayList<Vehicle>(s.vehiclesList);
		newSolution.tasksList = new ArrayList<Task>(s.tasksList);
		return newSolution;
	}

	/** Update Time of a vehicle**/
	public void updateTime(int vi) {

		//System.out.println("update time");
		Integer t = getFirstTaskVehicles(vi);
		if (t != null) {
			time[vi][t] = 1;
			Integer tj = 0;
			while (tj != null) {
				tj = post(t);
				if (tj != null) {
					time[vi][tj] = time[vi][t] + 1;
					t = tj;
				}
			}
		}



		//				Arrays.fill(this.time[vi], -1);
		//				if (this.getFirstTaskVehicles(vi) != null) {
		//					Integer ti = this.getFirstTaskVehicles(vi);
		//					this.time[vi][ti.intValue()] = 1;	
		//					Integer tj;
		//					do{
		//						tj = this.getNextAction(ti);
		//						if (tj != null) {
		//							this.time[vi][tj] = this.time[vi][ti]+1;
		//							ti = tj;
		//						}
		//					}while (tj != null);
		//				}
		//System.out.println("end update time");

	}

	/**
	 * Build the plan of every vehicle of the solution
	 * @param print: show sysout
	 * @return a list of Plan. First element of the list is the plan of the first vehicle.
	 */
	public List<Plan> toPlans(boolean print) {
		List<Plan> plans = new ArrayList<Plan>();

		for (Vehicle v : vehiclesList) {
			plans.add(toPlan(v.id(), print));
		}

		if (print) {
			System.out.println("Total cost : " + totalCost());
		}

		return plans;
	}

	/**
	 * Build the plan of a given vehicle whose id is vID
	 * @param vID
	 * @param print: show sysout
	 * @return the plan of the vehicle of id vID
	 */
	public Plan toPlan(int vID, boolean print) {
		Plan plan = null;

		if (firstTaskVehicles[vID] == null) {
			return Plan.EMPTY;

		} else {
			City currentCity = vehiclesList.get(vID).homeCity();
			plan = new Plan(currentCity);

			// move to pick up first task
			for (City city : currentCity.pathTo(tasksList.get(firstTaskVehicles[vID]/2).pickupCity)) {
				plan.appendMove(city);
			}

			currentCity = tasksList.get(firstTaskVehicles[vID]/2).pickupCity;

			plan.appendPickup(tasksList.get(firstTaskVehicles[vID]/2));

			Integer action = nextAction[firstTaskVehicles[vID]];
			while (action != null) {
				// next task id to handle = nextAction / 2
				// pick up or deliver = nextAction % 2  /// 0: pickup, 1: deliver

				int taskId = action / 2;
				int p_Or_d = action % 2;

				if (p_Or_d == 0) {
					for (City city : currentCity.pathTo(tasksList.get(taskId).pickupCity)) {
						plan.appendMove(city);
					}
					plan.appendPickup(tasksList.get(taskId));
					currentCity = tasksList.get(taskId).pickupCity;

				}
				else {
					for (City city : currentCity.pathTo(tasksList.get(taskId).deliveryCity)) {
						plan.appendMove(city);
					}
					plan.appendDelivery(tasksList.get(taskId));
					currentCity = tasksList.get(taskId).deliveryCity;
				}

				action = nextAction[action];
			}
			if (print) {
				System.out.println("-----------------------");
				System.out.println("Vehicle ID "+ vID + ", cost :" + totalCost(vID) +", color:" + vehiclesList.get(vID).color());
				System.out.println("-----------------------");
				for (Action A : plan) {
					System.out.println(A);
				}
			}
		}

		return plan;
	}


	/**
	 * computes the total cost of the solution
	 * @return
	 */
	public double totalCost(){
		double cost = 0;

		for (Vehicle v : vehiclesList) {
			cost = cost + totalCost(v.id());
		}
		return cost;
	}

	/**
	 * computes the cost of the plan of the vehicle of id [vID]
	 * @param vID
	 * @return
	 */
	public double totalCost(int vID) {
		double cost = 0;

		Plan p = this.toPlan(vID, false);
		if (p != null) {
			cost += p.totalDistance()*vehiclesList.get(vID).costPerKm();
		}

		return cost;
	}
}
