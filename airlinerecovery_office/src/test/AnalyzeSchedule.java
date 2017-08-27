package test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.LineValueComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class AnalyzeSchedule {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Parameter.isReadFixedRoutes = false;
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		Scanner sn = null;
		try {
			//sn = new Scanner(new File("linearsolution_30_421761.807_15.8.csv"));
			//sn = new Scanner(new File("linearsolution_60_423292.675_19.1.csv"));
			sn = new Scanner(new File("fixschedule_rachel.csv"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<LineValue> lvList = new ArrayList<>();

		while (sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();

			if (nextLine.equals("")) {
				break;
			}

			Scanner innerSn = new Scanner(nextLine);
			innerSn.useDelimiter(",");

			String nnn = innerSn.next();

			LineValue lv = new LineValue();
			lv.line = nextLine;
			lv.value = innerSn.nextDouble();

			readLine(scenario, lv.line);
		}

		System.out.println("--------------------------------------");
		
		int totalN = 0;
		
		int maxDelay = 0;
		
		for(Flight f:scenario.flightList){
			if(f.possibleDelaySet.size() > 0){
				boolean isDisplay = false;
				
				for(int delay:f.possibleDelaySet){
					if(delay > 60){
						isDisplay = true;
						break;
					}
				}
				
				if(isDisplay){
					System.out.print("f:"+f.id+" "+f.leg.originAirport.id+"->"+f.leg.destinationAirport.id+" ("+f.takeoffTime+")->("+f.landingTime+")    [");
					
					for(int delay:f.possibleDelaySet){
						System.out.print(delay+", ");
						
						if(delay > maxDelay){
							maxDelay = delay;
						}
					}
					System.out.println("]");
					
					totalN++;
				}		
			}
			
		}
		
		System.out.println("totalN:"+totalN+"  maxDelay："+maxDelay);
		
	}
	
	public static void readLine(Scenario scenario, String line){
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		
		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;
		List<Flight> flightList = new ArrayList<>();
		
		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
			} else if (flightArray[0].equals("s")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);
				f.isFixed = true;

				Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2]) - 1);
				f2.isFixed = true;

				a.fixedDestination = scenario.flightList
						.get(Integer.parseInt(flightArray[2]) - 1).leg.destinationAirport;
			
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f2.leg.destinationAirport;
				f.actualTakeoffT = Integer.parseInt(flightArray[3]);
				f.actualLandingT = Integer.parseInt(flightArray[4]);
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
				
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

	}

}
