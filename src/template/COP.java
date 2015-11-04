package template;

import java.util.List;

public class COP {
		// X = {nextTask, time, vehicle} set of variables
		setVariables X;
		// D = {d1,...,dn} set of domains
		int D;
		// C = {c1,...,cp} set of constraints
		int C;
		// f objective function
		int f;
		
		public class setVariables {
			List<Integer> nextTask;
			List<Integer> time;
			List<Integer> vehicle;
		}
}

