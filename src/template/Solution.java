package template;

import java.util.List;

public class Solution {

	Integer[] nextTaskActions;
	Integer[] nextTaskVehicles;
	Integer[] time;
	Integer[] vehicles;
	
	public Solution(int Nv, int Nt) {
		nextTaskActions = new Integer[2*Nt];
		nextTaskVehicles = new Integer[Nv];
		time = new Integer[Nv];
		vehicles = new Integer[2*Nt];
	}
	
	public Integer getNextTaskActions(int index) {
		return nextTaskActions[index];
	}
	
	public void setNextTaskActions(int index,int newValue) {
		nextTaskActions[index] = newValue;
	}
	
	public Integer getNextTaskVehicles(int index) {
		return nextTaskVehicles[index];
	}
	
	public void setNextTaskVehicles(int index,int newValue) {
		nextTaskVehicles[index] = newValue;
	}
}
