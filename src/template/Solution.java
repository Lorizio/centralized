package template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import logist.plan.Action;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class Solution {
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
	
	public Solution(int vehiculesSize) {
		this.numberOfVehicle = vehiculesSize;
		this.numberOfTasks = 0;

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
		Integer tPre1 = v; //A1.getNextTaskVehicles(vi);// previous task of action1
		Integer t1 = A1.getFirstTaskVehicles(tPre1); // task1
		int count = 1;

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
		// t1 is a pickup		
		if (((t1 % 2 == 0) && (A1.time[v][t2] >= A1.time[v][t1+1])) 
				||((t2 % 2 != 0)&&(A1.time[v][t1] <= A1.time[v][t2-1]))) {
			return null;
		}
		else {
			if (tPost1 == t2) {
				// t2 is done just after t1
				A1.setNextAction(tPre1, t2);
				A1.setNextAction(t2, t1);
				A1.setNextAction(t1, tPost2);
			}
			else {
				A1.setNextAction(tPre1, t2);
				A1.setNextAction(tPre2, t1);
				A1.setNextAction(t2, tPost1);
				A1.setNextAction(t1, tPost2);
			}
			int timeT1 = A1.time[v][t1];
			A1.time[v][t1] = A1.time[v][t2];
			A1.time[v][t2] = timeT1;
			//A1.updateTime(v);

			return A1;
		}
	}


	public List<Plan> toPlans(List<Vehicle> vehicles, TaskSet tasks) {
		List<Plan> plans;
		plans = new ArrayList<Plan>();
		List<Task> tasksList = new ArrayList<Task>();
		for (Task t : tasks) {
			tasksList.add(t);
		}
		double couttotal = 0;

		// for each vehicle we will create a plan
		for (int i = 0; i < firstTaskVehicles.length; i++) {

			// if vehicle has no first task --> plan is empty and the vehicle won't be used
			if (firstTaskVehicles[i] == null) {
				plans.add(Plan.EMPTY);

			} 
			else {
				City currentCity = vehicles.get(i).homeCity();
				Plan plan = new Plan(currentCity);

				// move to pick up first task
				for (City city : currentCity.pathTo(tasksList.get(firstTaskVehicles[i]/2).pickupCity)) {
					plan.appendMove(city);
				}

				currentCity = tasksList.get(firstTaskVehicles[i]/2).pickupCity;

				plan.appendPickup(tasksList.get(firstTaskVehicles[i]/2));

				Integer action = nextAction[firstTaskVehicles[i]];
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
				System.out.println("-----------------------");
				couttotal = couttotal + plan.totalDistance()*vehicles.get(i).costPerKm();
				System.out.println("Vehicle "+i + ",  cost :"+plan.totalDistance()*vehicles.get(i).costPerKm()+", color:"+vehicles.get(i).color().toString());
				System.out.println("-----------------------");
				for (Action A : plan) {
					System.out.println(A.toString());
				}
				plans.add(plan);

			}
			System.out.println("cout total :" + couttotal);

		}

		return plans;
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
			System.err.println("pre() : fromAction et targetAction ne sont pas sur le même chemin");
		}

		return preAction;
	}


	/** Changing the vehicle for a task (pickup and delivery) **/
	public Solution changingVehicle(int vFrom, int vTo) {
		System.out.println("Changing vehicle");
		
		Solution A1 = clone(this);

		Integer firstTaskPickup = A1.getFirstTaskVehicles(vFrom);
		//System.out.println("firstTaskpickup : " + firstTaskPickup);
		Integer postTaskPickup = A1.post(firstTaskPickup);
/*
		Integer firstTaskDelivery = firstTaskPickup + 1;
		System.out.println(firstTaskPickup);
		Integer preTaskDelivery = A1.pre(firstTaskDelivery, firstTaskPickup);
		Integer postTaskDelivery = A1.post(firstTaskDelivery);

		if (preTaskDelivery == postTaskPickup) { // D_i immediately follows P_i
			A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
			A1.setNextAction(firstTaskDelivery, A1.getFirstTaskVehicles(vTo));
			A1.setNextAction(firstTaskPickup, firstTaskDelivery);
			A1.setFirstTaskVehicles(vTo, firstTaskPickup);
		} else {
			A1.setNextAction(preTaskDelivery, postTaskDelivery);
			A1.setFirstTaskVehicles(vFrom, A1.findNextPickup(firstTaskPickup));
			A1.setNextAction(firstTaskDelivery, A1.getFirstTaskVehicles(vTo));
			A1.setNextAction(firstTaskPickup, firstTaskDelivery);
			A1.setFirstTaskVehicles(vTo, firstTaskPickup);
		}

		A1.vehicles[firstTaskPickup/2] = vTo;
		A1.updateTime(vFrom);
		A1.updateTime(vTo);
		System.out.println("end changingVehicle");

		return A1;
		*/

		System.out.println("FIRSTTASKPICKUP : " + firstTaskPickup);
		// Delivery
		if (firstTaskPickup % 2 != 0) {
			A1.setFirstTaskVehicles(vFrom,A1.getNextAction(firstTaskPickup));
			A1.setNextAction(firstTaskPickup, A1.getFirstTaskVehicles(vTo));
			A1.setFirstTaskVehicles(vTo, firstTaskPickup);
		}
		// Pick up
		else {
			// We have to assign the pickup t and the deliver to the same vehicle
			if (A1.getNextAction(firstTaskPickup) == firstTaskPickup+1){
				A1.setFirstTaskVehicles(vFrom, A1.getNextAction(firstTaskPickup+1));
				A1.setNextAction(firstTaskPickup, firstTaskPickup+1);
				A1.setNextAction(firstTaskPickup+1, A1.getFirstTaskVehicles(vTo));
				A1.setFirstTaskVehicles(vTo, firstTaskPickup);
			}	
			else {
				Integer id = firstTaskPickup;
				int tPre=id;
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
		}
		A1.vehicles[firstTaskPickup/2] = vTo;
		A1.updateTime(vFrom);
		A1.updateTime(vTo);
		//System.out.println("apres updates");
		return A1;
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
		return newSolution;
	}

	/** Update Time of a vehicle**/
	public void updateTime(int vi) {

		System.out.println("update time");
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
				System.out.println("end update time");

	}

	public List<Plan> toPlans(List<Vehicle> vehicles, List<Task> tasksList) {
		List<Plan> plans;
		plans = new ArrayList<Plan>();
		
		double couttotal = 0;

		// for each vehicle we will create a plan
		for (int i = 0; i < firstTaskVehicles.length; i++) {

			// if vehicle has no first task --> plan is empty and the vehicle won't be used
			if (firstTaskVehicles[i] == null) {
				plans.add(Plan.EMPTY);

			} 
			else {
				City currentCity = vehicles.get(i).homeCity();
				Plan plan = new Plan(currentCity);

				// move to pick up first task
				for (City city : currentCity.pathTo(tasksList.get(firstTaskVehicles[i]/2).pickupCity)) {
					plan.appendMove(city);
				}

				currentCity = tasksList.get(firstTaskVehicles[i]/2).pickupCity;

				plan.appendPickup(tasksList.get(firstTaskVehicles[i]/2));

				Integer action = nextAction[firstTaskVehicles[i]];
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
				System.out.println("-----------------------");
				couttotal = couttotal + plan.totalDistance()*vehicles.get(i).costPerKm();
				System.out.println("Vehicle "+i + ",  cost :"+plan.totalDistance()*vehicles.get(i).costPerKm()+", color:"+vehicles.get(i).color().toString());
				System.out.println("-----------------------");
				for (Action A : plan) {
					System.out.println(A.toString());
				}
				plans.add(plan);

			}
			System.out.println("cout total :" + couttotal);

		}

		return plans;
	}

	public double cost(){
		
		return 0;
	}
}
