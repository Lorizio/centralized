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

	Integer[] nextTaskActions;
	Integer[] nextTaskVehicles;
	Integer[][] time;
	Integer[] vehicles;
	
	public Solution(int Nv, int Nt) {
		nextTaskActions = new Integer[2*Nt];
		nextTaskVehicles = new Integer[Nv];
		time = new Integer[Nv][2*Nt];
		vehicles = new Integer[Nt];
	}
	
	public Integer getNextTaskActions(int index) {
		return nextTaskActions[index];
	}
	
	public void setNextTaskActions(int index,Integer newValue) {
		nextTaskActions[index] = newValue;
	}
	
	public Integer getNextTaskVehicles(int index) {
		return nextTaskVehicles[index];
	}
	
	public void setNextTaskVehicles(int index,Integer newValue) {
		nextTaskVehicles[index] = newValue;
	}
	
	public List<Plan> toPlans(List<Vehicle> vehicles, TaskSet tasks) {
		List<Plan> plans;
		plans = new ArrayList<Plan>();
		List<Task> tasksList = new ArrayList<Task>();
		for (Task t : tasks) {
			tasksList.add(t);
		}

		// for each vehicle we will create a plan
		for (int i = 0; i < nextTaskVehicles.length; i++) {

			// if vehicle has no first task --> plan is empty and the vehicle won't be used
			if (nextTaskVehicles[i] == null) {
				plans.add(Plan.EMPTY);

			} 
			else {
				City currentCity = vehicles.get(i).homeCity();
				Plan plan = new Plan(currentCity);

				// move to pick up first task
				for (City city : currentCity.pathTo(tasksList.get(nextTaskVehicles[i]/2).pickupCity)) {
					plan.appendMove(city);
				}

				currentCity = tasksList.get(nextTaskVehicles[i]/2).pickupCity;

				plan.appendPickup(tasksList.get(nextTaskVehicles[i]/2));

				Integer action = nextTaskActions[nextTaskVehicles[i]];
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
					
					action = nextTaskActions[action];
				}
				System.out.println("-----------------------");
				System.out.println("Vehicle "+i + ",  cost :"+plan.totalDistance()*vehicles.get(i).costPerKm()+", color:"+vehicles.get(i).color().toString());
				System.out.println("-----------------------");
				for (Action A : plan) {
					System.out.println(A.toString());
				}
				plans.add(plan);

			}
			
		}

		return plans;
	}
}
