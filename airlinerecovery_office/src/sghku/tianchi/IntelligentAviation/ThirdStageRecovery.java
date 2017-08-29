package sghku.tianchi.IntelligentAviation;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class ThirdStageRecovery {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		Parameter.onlySignChangeDisruptedPassenger = true;  //只能转签受影响乘客

		runOneIteration(false);

	}
	public static void runOneIteration(boolean isFractional){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
	
			
	}
}
