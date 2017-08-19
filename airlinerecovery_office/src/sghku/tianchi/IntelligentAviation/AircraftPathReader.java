package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.LineValueComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.LineValue;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class AircraftPathReader {
	public Set<Integer> idSet = new HashSet<>();
	//读取已经固定的飞机路径
	public void read(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");
		
		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID-1);
		if(a.isFixed){
			System.out.println("we find aircraft error "+a.id);
		}
		a.isFixed = true;
		
		double flow = sn.nextDouble();
		
		a.fixedDestination = a.initialLocation;
		
		while(sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");
			
			if(flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);
				f.isFixed = true;
				f.actualTakeoffT = Integer.parseInt(flightArray[2]);
				f.actualLandingT = Integer.parseInt(flightArray[3]);
				
				a.fixedDestination = f.leg.destinationAirport;
				
				if(idSet.contains(f.id)){
					System.out.println("error 1 "+f.id);
				}else{
					idSet.add(f.id);
				}
			}else if(flightArray[0].equals("s")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);			
				f.isFixed = true;
				
				
				if(idSet.contains(f.id)){
					System.out.println("error 2");
				}else{
					idSet.add(f.id);
				}
				
				Flight f2 = scenario.flightList.get(Integer.parseInt(flightArray[2])-1);			
				f2.isFixed = true;
				
				if(idSet.contains(f2.id)){
					System.out.println("error 3");
				}else{
					idSet.add(f2.id);
				}
				
				f.actualOrigin = f.leg.originAirport;
				f.actualDestination = f2.leg.destinationAirport;
				f.actualTakeoffT = Integer.parseInt(flightArray[3]);
				f.actualLandingT = Integer.parseInt(flightArray[4]);
				f.isStraightenedFirst = true;
				f2.isStraightenedSecond = true;
				
				a.fixedDestination = scenario.flightList.get(Integer.parseInt(flightArray[2])-1).leg.destinationAirport;
			}else if(flightArray[0].equals("d")) {
				
				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2])-1);
			}
		}
	}
	//检查某一个路径是否约束冲突
	public boolean check(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");
		
		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID-1);
		
		double flow = sn.nextDouble();
		
		a.fixedDestination = a.initialLocation;
		
		boolean isFeasible = true;
		if(a.isFixed){
			isFeasible = false;
		}
		
		if(isFeasible){
			while(sn.hasNext()) {
				String flightStr = sn.next();
				String[] flightArray = flightStr.split("_");
				
				if(flightArray[0].equals("n")) {
					Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);
					
					if(f.isFixed) {
						isFeasible = false;
					}
					a.fixedDestination = f.leg.destinationAirport;
				}else if(flightArray[0].equals("s")) {
					Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);			
					if(f.isFixed) {
						isFeasible = false;
					}
					f = scenario.flightList.get(Integer.parseInt(flightArray[2])-1);			
					if(f.isFixed) {
						isFeasible = false;
					}
					a.fixedDestination = scenario.flightList.get(Integer.parseInt(flightArray[2])-1).leg.destinationAirport;
				}else if(flightArray[0].equals("d")) {
					
					a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2])-1);
				}
			}
		}
	
		for(int type=0;type<Parameter.TOTAL_AIRCRAFTTYPE_NUM;type++) {
			if(a.fixedDestination.finalAircraftNumber[type] <= 0) {
				isFeasible = false;
			}
		}
		
		
		return isFeasible;
	}
	//固定飞机路径
	public void fixAircraftRoute(Scenario scenario, int number) {
		Scanner sn = null;
		try {
			sn = new Scanner(new File("linearsolution.csv"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		List<LineValue> lvList = new ArrayList<>();
		
		while(sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();
			
			if(nextLine.equals("")) {
				break;
			}
			System.out.println(nextLine);
			Scanner innerSn = new Scanner(nextLine);
			innerSn.useDelimiter(",");
			
			String nnn = innerSn.next();

			LineValue lv = new LineValue();
			lv.line = nextLine;
			lv.value = innerSn.nextDouble();
			
			if(lv.value > 0.2-1e-6) {
				lvList.add(lv);
			}
			
		}
		
		Collections.sort(lvList, new LineValueComparator());
		
		try {
			MyFile.creatTxtFile("fixschedule");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("-------------------------------------");
		int nnn = 0;
		for(int i=0;i<lvList.size();i++) {
			LineValue lv = lvList.get(i);
			
			if(check(lv.line, scenario)) {
				update(lv.line, scenario);
				//System.out.println(lv.line);
				try {
					MyFile.writeTxtFile(lv.line);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				nnn++;
				
				if(nnn >= number){
					break;
				}
			}/*else {
				System.out.println("violation");
			}*/
		}
		System.out.println("nnn:"+nnn);
	}
	//固定某一个飞机路径，更新对应的信息
	public void update(String line, Scenario scenario) {
		Scanner sn = new Scanner(line);
		sn.useDelimiter(",");
		
		int aircraftID = sn.nextInt();
		Aircraft a = scenario.aircraftList.get(aircraftID-1);
		a.isFixed = true;
		
		double flow = sn.nextDouble();
		
		a.fixedDestination = a.initialLocation;
				
		while(sn.hasNext()) {
			String flightStr = sn.next();
			String[] flightArray = flightStr.split("_");
			
			if(flightArray[0].equals("n")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);
				
				f.isFixed = true;
				a.fixedDestination = f.leg.destinationAirport;
			}else if(flightArray[0].equals("s")) {
				Flight f = scenario.flightList.get(Integer.parseInt(flightArray[1])-1);			
				f.isFixed = true;
				
				f = scenario.flightList.get(Integer.parseInt(flightArray[2])-1);			
				f.isFixed = true;
				
				a.fixedDestination = scenario.flightList.get(Integer.parseInt(flightArray[2])-1).leg.destinationAirport;
			}else if(flightArray[0].equals("d")) {
				
				a.fixedDestination = scenario.airportList.get(Integer.parseInt(flightArray[2])-1);
			}
		}
		
		a.fixedDestination.finalAircraftNumber[a.type-1]--;
	}
}
