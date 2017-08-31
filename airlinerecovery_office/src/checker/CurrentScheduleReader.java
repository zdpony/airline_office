package checker;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class CurrentScheduleReader {
	public static void main(String[] args) {
		Parameter.isPassengerCostConsidered = false;
		Parameter.isReadFixedRoutes = false;
		
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		
		int aircraftType1 = 0;
		int aircraftType2 = 0;
		int aircraftType3 = 0;
		int aircraftType4 = 0;
		
		for(Aircraft a:scenario.aircraftList) {
			if(a.type == 1) {
				aircraftType1++;
			}else if(a.type == 2) {
				aircraftType2++;
			}else if(a.type == 3) {
				aircraftType3++;
			}else if(a.type == 4) {
				aircraftType4++;
			}
		}
		
		System.out.println(aircraftType1+" "+aircraftType2+" "+aircraftType3+" "+aircraftType4);
		
		/*Map<Integer,ObjectValue> ovMap = new HashMap<Integer, ObjectValue>();
		for(Flight f:scenario.flightList) {
			if(f.initialTakeoffT >= 7*1440 && f.initialTakeoffT <= 8*1440) {
				ObjectValue pv = ovMap.get(f.leg.originAirport.id);
				if(pv == null) {
					pv = new ObjectValue();
					pv.id = f.leg.originAirport.id;
					ovMap.put(f.leg.originAirport.id, pv);
				}
				pv.value++;
				
				pv = ovMap.get(f.leg.destinationAirport.id);
				if(pv == null) {
					pv = new ObjectValue();
					pv.id = f.leg.destinationAirport.id;
					ovMap.put(f.leg.destinationAirport.id, pv);
				}
				pv.value++;
			}
		}
		
		List<ObjectValue> ovList = new ArrayList<>();
		for(int key:ovMap.keySet()) {
			ovList.add(ovMap.get(key));
		}
		
		Collections.sort(ovList, new ObjectValueComparator());
		
		for(ObjectValue ov:ovList) {
			System.out.println(ov.id+"  "+ov.value);
		}
		System.exit(1);*/
		
		CurrentScheduleReader currentScheduleReader = new CurrentScheduleReader();
		currentScheduleReader.read(scenario);
		
		int typechangeNum1 = 0;
		int nonchangeNum1 = 0;
		
		for(Aircraft a:scenario.aircraftList) {
			for(Flight f:a.flightList) {
				if(f.initialTakeoffT >= 6*1440 && f.initialTakeoffT <= 6*1440+12*60) {
					//if(f.initialAircraft.id != a.id) {
					if(f.initialAircraftType != a.type) {
						typechangeNum1++;
						System.out.println("type change:"+f.takeoffTime+" "+f.leg.originAirport+"->"+f.leg.destinationAirport);
					}else {
						nonchangeNum1++;
					}
				}
			}
		}
		System.out.println("typechangeNum1:"+typechangeNum1+"  nonchangeNum1:"+nonchangeNum1);
		
		System.out.println("---------------------------------------------------------------------");
		
		int typechangeNum2 = 0;
		int nonchangeNum2 = 0;
		
		for(Aircraft a:scenario.aircraftList) {
			for(Flight f:a.flightList) {
				if(f.initialTakeoffT >= 8*1440 && f.initialTakeoffT <= 9*1440) {
					//if(f.initialAircraft.id != a.id) {
					if(f.initialAircraftType != a.type) {
						typechangeNum2++;
					}else {
						nonchangeNum2++;
					}
				}
			}
		}
		
		System.out.println("typechangeNum2:"+typechangeNum2+"  nonchangeNum2:"+nonchangeNum2);
		
		System.out.println("---------------------------------------------------------------------");

		
		int typechangeNum3 = 0;
		int nonchangeNum3 = 0;
		
		for(Aircraft a:scenario.aircraftList) {
			for(Flight f:a.flightList) {
				if(!f.isStraightened) {
					if(f.initialTakeoffT >= 7*1440 && f.initialTakeoffT <= 8*1440) {
						//if(f.initialAircraft.id != a.id) {
						if(f.initialAircraftType != a.type) {
							typechangeNum3++;
							System.out.println("type change:"+f.takeoffTime+" "+f.leg.originAirport+"->"+f.leg.destinationAirport+" ");
						}else {
							nonchangeNum3++;
						}
					}
				}
				
			}
		}
		
		System.out.println("typechangeNum3:"+typechangeNum3+"  nonchangeNum3:"+nonchangeNum3);
		System.out.println("---------------------------------------------------------------------");

		Set<Integer> affectAirportSet = new HashSet();
		affectAirportSet.add(49);
		affectAirportSet.add(50);
		affectAirportSet.add(52);
		affectAirportSet.add(32);
		affectAirportSet.add(38);
		affectAirportSet.add(61);
		affectAirportSet.add(25);
		affectAirportSet.add(67);
		affectAirportSet.add(62);
		affectAirportSet.add(27);
		affectAirportSet.add(57);
		
		int n4 = 0;
		for(Flight f:scenario.flightList) {
			if(f.initialTakeoffT >= 7*1440 && f.initialTakeoffT <= 8*1440) {
				if(affectAirportSet.contains(f.leg.originAirport.id) || affectAirportSet.contains(f.leg.destinationAirport.id)) {
					n4++;
				}
			}
		}
		System.out.println("n4:"+n4);
		
		System.out.println("********************analysis of delays************************");
		int day = 7;
		for(Flight f:scenario.flightList) {
			if(f.initialTakeoffT >= day*1440 && f.initialTakeoffT <= (day+1)*1440) {
				int delay = f.actualTakeoffT - f.initialTakeoffT;
				
				if(delay > 60) {
					System.out.println("large delay:"+delay/60.+"  "+f.leg.originAirport+"->"+f.leg.destinationAirport.id+"  "+f.takeoffTime+"  "+f.landingTime);
				}
			}
		}
	
	}
	
	public void read(Scenario scenario) {
		try {
			for(Aircraft a:scenario.aircraftList) {
				a.flightList.clear();
			}
			
			Scanner sn = new Scanner(new File("result\\pony_588432.8_200.csv"));
			
			while(sn.hasNextLine()) {
				String nextLine = sn.nextLine().trim();
		
				if(nextLine.equals("")) {
					break;
				}
				
				Scanner innerSn = new Scanner(nextLine);
				innerSn.useDelimiter(",");
				
				int flightId = innerSn.nextInt();
				int originId = innerSn.nextInt();
				int destId = innerSn.nextInt();
				
				String takeoffString = innerSn.next();
				String[] dayandtime = takeoffString.split(" ");
				int takeoffT = Integer.parseInt(dayandtime[0].split("/")[2])*1440+Integer.parseInt(dayandtime[1].split(":")[0])*60+Integer.parseInt(dayandtime[1].split(":")[1]);
				
				
				String landingString = innerSn.next();
				dayandtime = landingString.split(" ");
				int landingT = Integer.parseInt(dayandtime[0].split("/")[2])*1440+Integer.parseInt(dayandtime[1].split(":")[0])*60+Integer.parseInt(dayandtime[1].split(":")[1]);
				
				int aircraftId = innerSn.nextInt();
				
				int isCancelled = innerSn.nextInt();
				int isStraightend = innerSn.nextInt();
				int isDeadhead = innerSn.nextInt();

				Aircraft aircraft = scenario.aircraftList.get(aircraftId-1);
				
				if(flightId < 9001) {
					Flight f = scenario.flightList.get(flightId-1);
					if(isCancelled == 0) {
						if(isStraightend == 1) {
							//生成联程拉直航班
							
							ConnectingFlightpair cp = f.connectingFlightpair;
							
							int flyTime = cp.straightenLeg.flytimeArray[aircraft.type-1];
							
							if(flyTime <= 0){  // if cannot retrieve fly time
								flyTime = cp.firstFlight.initialLandingT-cp.firstFlight.initialTakeoffT+ cp.secondFlight.initialLandingT-cp.secondFlight.initialTakeoffT;
							}
							
							Flight straightenedFlight = new Flight();
							straightenedFlight.isStraightened = true;
							straightenedFlight.connectingFlightpair = cp;
							straightenedFlight.leg = cp.straightenLeg;
							
							straightenedFlight.flyTime = flyTime;
									
							straightenedFlight.initialTakeoffT = cp.firstFlight.initialTakeoffT;
							straightenedFlight.initialLandingT = straightenedFlight.initialTakeoffT + flyTime;
							
							straightenedFlight.isAllowtoBringForward = cp.firstFlight.isAllowtoBringForward;
							straightenedFlight.isAffected = cp.firstFlight.isAffected;
							straightenedFlight.isDomestic = true;
							straightenedFlight.earliestPossibleTime = cp.firstFlight.earliestPossibleTime;
							straightenedFlight.latestPossibleTime = cp.firstFlight.latestPossibleTime;
						
							straightenedFlight.actualTakeoffT = takeoffT;
							straightenedFlight.actualLandingT = landingT;
							
							straightenedFlight.aircraft = aircraft;
							aircraft.flightList.add(straightenedFlight);
						}else {
							f.actualTakeoffT = takeoffT;
							f.actualLandingT = landingT;
							f.aircraft = aircraft;
							aircraft.flightList.add(f);
						}
					}
				}else {
					
					System.out.println("this is a dead head arc");
					System.exit(1);
				}
			}
			
			for(Flight f:scenario.flightList) {
				f.isCancelled = true;
			}
			for(Aircraft a:scenario.aircraftList) {
				for(Flight f:a.flightList) {
					if(f.isStraightened) {
						f.connectingFlightpair.firstFlight.isCancelled = false;
						f.connectingFlightpair.secondFlight.isCancelled = false;
					}else {
						f.isCancelled = false;
					}
				}
				
				Collections.sort(a.flightList, new FlightComparator2());
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
