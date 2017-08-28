package checker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class ArcChecker {

	public static Map<Integer, Set<String>> aircraftConnectingArcMap = new HashMap<>();
	public static Map<Integer, Set<String>> aircraftFlightArcMap = new HashMap<>();
	public static Map<Integer, Set<String>> aircraftStraightenedArcMap = new HashMap<>();

	
	//检查optimal solution是否在我们的arc中出现
	public static void init() {
		// TODO Auto-generated method stub
		Parameter.isReadFixedRoutes = false;
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		String fileName = "linearsolution_30_421761.807_15.8.csv";
		
		Scanner sn = null;
		try {
			sn = new Scanner(new File(fileName));			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

		for(Aircraft a:scenario.aircraftList) {
			if(Math.abs(a.flow-1) > 1e-5) {
				System.out.println("aircraft flow error");
			}
			
			System.out.println(aircraftFlightArcMap.get(a.id).size()+"  "+aircraftConnectingArcMap.get(a.id).size()+"  "+aircraftStraightenedArcMap.get(a.id).size());
		}
	
	}
	
	public static void checkFlightArcs(Aircraft a, List<FlightArc> flightArcList) {
		Set<String> wholeFlightArcSet = new HashSet<>();
		
		for(FlightArc arc:flightArcList) {
			if(!arc.flight.isStraightened) {
				wholeFlightArcSet.add(arc.flight.id+"_"+arc.takeoffTime);				
			}
		}
		
		Set<String> currentFlightArcSet = aircraftFlightArcMap.get(a.id);
		
		if(!wholeFlightArcSet.contains(currentFlightArcSet)) {
			System.out.println("we find exception : "+a.id);
		}
	}
	
	public static void readLine(Scenario scenario, String line){
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");

		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID - 1);
		
		Set<String> connectingArcSet = new HashSet<>();
		Set<String> flightArcSet = new HashSet<>();
		Set<String> straightenedArcSet = new HashSet<>();
		
		aircraftConnectingArcMap.put(a.id, connectingArcSet);
		aircraftConnectingArcMap.put(a.id, flightArcSet);
		aircraftConnectingArcMap.put(a.id, straightenedArcSet);

		double flow = sn.nextDouble();

		a.fixedDestination = a.initialLocation;
		a.flow += flow;
		List<Flight> flightList = new ArrayList<>();
		
		List<Flight> selectedFlightList = new ArrayList<>();
		
		while (sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");

			if (flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1]) - 1);

				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f.leg.destinationAirport;
				
				f.aircraft = a;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
				selectedFlightList.add(f);
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
				
				f.aircraft = a;
				
				f.possibleDelaySet.add(f.actualTakeoffT - f.initialTakeoffT);
				selectedFlightList.add(f);
			} else if (flightArray[0].equals("d")) {

				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2]) - 1);
			}
		}

		//处理联程拉直
		for(int i=0;i<selectedFlightList.size();i++) {
			Flight f1 = selectedFlightList.get(i);
			
			if(!f1.actualDestination.equals(f1.leg.destinationAirport)) {
				f1.connectingFlightpair.firstFlight.isStraightened = true;
				f1.connectingFlightpair.secondFlight.isStraightened = true;
				straightenedArcSet.add(f1.connectingFlightpair.firstFlight.id+"_"+f1.connectingFlightpair.secondFlight.id+"_"+f1.actualTakeoffT);
			}else {
				flightArcSet.add(f1.id+"_"+f1.actualTakeoffT);
			}
		}
		
		//处理联程航班
		for(int i=0;i<selectedFlightList.size()-1;i++) {
			Flight f1 = selectedFlightList.get(i);
			Flight f2 = selectedFlightList.get(i+1);
			
			if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id) {
				connectingArcSet.add(f1.id+"_"+f2.id+"_"+f1.actualTakeoffT+"_"+f2.actualTakeoffT);
			}
		}
		
	}

	
}
