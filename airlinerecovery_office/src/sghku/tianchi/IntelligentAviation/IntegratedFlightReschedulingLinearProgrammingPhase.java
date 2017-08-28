package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import javax.swing.plaf.synth.SynthSpinnerUI;

import checker.ArcChecker;
import sghku.tianchi.IntelligentAviation.algorithm.FlightDelayLimitGenerator;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructorBasedOnDelayAndEarlyLimit;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.OutputResultWithPassenger;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.FlightSectionItinerary;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Itinerary;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;
import sghku.tianchi.IntelligentAviation.model.CplexModel;
import sghku.tianchi.IntelligentAviation.model.CplexModelForPureAircraft;
import sghku.tianchi.IntelligentAviation.model.IntegratedCplexModel;
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class IntegratedFlightReschedulingLinearProgrammingPhase {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = false;
		Parameter.isReadFixedRoutes = true;
		Parameter.onlySignChangeDisruptedPassenger = false;
		
		runOneIteration(true, 70);
		
	}
		
	public static void runOneIteration(boolean isFractional, int fixNumber){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
				
		FlightDelayLimitGenerator flightDelayLimitGenerator = new FlightDelayLimitGenerator();
		flightDelayLimitGenerator.setFlightDelayLimit(scenario);
		
		/*for(Flight f:scenario.flightList){
			System.out.print(f.id+"  ");
			for(int[] timeLimit:f.timeLimitList){
				System.out.print("["+timeLimit[0]+","+timeLimit[1]+"] ");
			}
			System.out.println();
		}
		
		try {
			Scanner sn = new Scanner(new File("flightdelay.csv"));
		
			sn.nextLine();
			while(sn.hasNextLine()){
				String nextLine = sn.nextLine();
				if(nextLine.trim().equals("")){
					break;
				}
				
				Scanner innerSn = new Scanner(nextLine);
				innerSn.useDelimiter(",");
				int fId = innerSn.nextInt();
				
				Flight f = scenario.flightList.get(fId-1);
				
				String[] delayArray = innerSn.next().split("_");
			
				for(String delay:delayArray){
					int d = Integer.parseInt(delay);
					int t = f.initialTakeoffT + d;
					boolean isInclude = false;
					for(int[] timeLimit:f.timeLimitList){
						int startT = timeLimit[0];
						int endT = timeLimit[1];
						
						if(t >= startT && t <= endT){
							isInclude = true;
							break;
						}
					}
					
					if(!isInclude){
						System.out.println("error "+f.id+" delay:"+d+"  actual time:"+t);
					}
				}
				
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.exit(1);*/
		
		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();
		
		List<Aircraft> candidateAircraftList= new ArrayList<>();
		
		for(Flight f:scenario.flightList) {
			if(!f.isFixed) {
				if(f.isIncludedInConnecting) {
					//如果联程航班另一截没被fix，则加入candidate，否则只能cancel（因为联程必须同一个飞机）
					if(!f.brotherFlight.isFixed) {  
						candidateFlightList.add(f);					
					}
				}else {
					candidateFlightList.add(f);					
				}
			}
		}
		
		for(ConnectingFlightpair cf:scenario.connectingFlightList) {
			if(!cf.firstFlight.isFixed && !cf.secondFlight.isFixed) {
				candidateConnectingFlightList.add(cf);
			}				
		}
		
		//将所有candidate flight设置为not cancel
		for(Flight f:candidateFlightList){
			f.isCancelled = false;
		}
		
		for(Aircraft a:scenario.aircraftList) {
			if(!a.isFixed) {
				candidateAircraftList.add(a);
			}
		}
		
		for(Aircraft a:candidateAircraftList){
			for(Flight f:candidateFlightList){
				if(!a.tabuLegs.contains(f.leg)){
					a.singleFlightList.add(f);
				}
			}
			for(ConnectingFlightpair cf:candidateConnectingFlightList){
				if(!a.tabuLegs.contains(cf.firstFlight.leg) && !a.tabuLegs.contains(cf.secondFlight.leg)){
					a.connectingFlightList.add(cf);
				}
			}
		}
		
		
		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}
		
		for(Aircraft a:scenario.aircraftList) {
			if(a.isFixed) {				
				a.fixedDestination.finalAircraftNumber[a.type-1]--;
			}
		}
		//更新起降约束
		for(Flight f:scenario.flightList) {
			if(f.isFixed) {			
				if(scenario.affectedAirportSet.contains(f.actualOrigin)) {
					for(long i=Parameter.airportBeforeTyphoonTimeWindowStart;i<=Parameter.airportBeforeTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualTakeoffT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualOrigin.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualOrigin.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}
					
					for(long i=Parameter.airportAfterTyphoonTimeWindowStart;i<=Parameter.airportAfterTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualTakeoffT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualOrigin.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualOrigin.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}					
				}else if(scenario.affectedAirportSet.contains(f.actualDestination)) {
					for(long i=Parameter.airportBeforeTyphoonTimeWindowStart;i<=Parameter.airportBeforeTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualLandingT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualDestination.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualDestination.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}
					
					for(long i=Parameter.airportAfterTyphoonTimeWindowStart;i<=Parameter.airportAfterTyphoonTimeWindowEnd;i+=5) {
						if(i == f.actualLandingT) {
							int n = scenario.affectAirportLdnTkfCapacityMap.get(f.actualDestination.id+"_"+i);
							scenario.affectAirportLdnTkfCapacityMap.put(f.actualDestination.id+"_"+i, n-1);
							
							if(n-1 < 0) {
								System.out.println("error : negative capacity");
							}
						}
					}	
				}			
			}
		}
		
		//更新停机约束
		for(Aircraft a:scenario.aircraftList) {
			if(a.isFixed) {
				if(a.fixedFlightList.size() > 0) {
					for(int i=0;i<a.fixedFlightList.size()-1;i++) {
						Flight f1 = a.fixedFlightList.get(i);
						Flight f2 = a.fixedFlightList.get(i+1);
					
						if(!f1.actualDestination.equals(f2.actualOrigin)) {
							System.out.println("error aircraft routes");
						}
						if(f1.actualLandingT >= f2.actualTakeoffT) {
							System.out.println("error connection time");
						}
						
						if(scenario.affectedAirportSet.contains(f1.actualDestination.id)) {
							if(f1.actualLandingT <= Parameter.airport49_50_61ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport49_50_61ParkingLimitEnd) {
								int limit = scenario.affectedAirportParkingLimitMap.get(f1.actualDestination.id);
								scenario.affectedAirportParkingLimitMap.put(f1.actualDestination.id, limit-1);
								
								if(limit-1 < 0) {
									System.out.println("negative airport limit");
								}
							}
						}
						
						//判断25和67机场停机约束
						if(f1.actualDestination.id == 25){
							if(f1.actualLandingT <= Parameter.airport25_67ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport25_67ParkingLimitEnd) {
								scenario.airport25ParkingLimit--;
								
								if(scenario.airport25ParkingLimit< 0) {
									System.out.println("negative airport limit");
								}
							}
						}
						if(f1.actualDestination.id == 67){
							if(f1.actualLandingT <= Parameter.airport25_67ParkingLimitStart && f2.actualTakeoffT >= Parameter.airport25_67ParkingLimitEnd) {
								scenario.airport67ParkingLimit--;
								
								if(scenario.airport67ParkingLimit< 0) {
									System.out.println("negative airport limit");
								}
							}
						}
					}
				}		
			}
		}
		
		for(Airport a:scenario.airportList){
			for(int type=0;type<Parameter.TOTAL_AIRCRAFTTYPE_NUM;type++) {
				if(a.finalAircraftNumber[type] < 0){
					System.out.println("error "+a.finalAircraftNumber+" "+a.id);
				}
			}		
		}
		
		//基于目前固定的飞机路径来进一步求解线性松弛模型
		solver(scenario, candidateAircraftList, candidateFlightList, candidateConnectingFlightList, isFractional);		
		
		//根据线性松弛模型来确定新的需要固定的飞机路径
		AircraftPathReader scheduleReader = new AircraftPathReader();
		scheduleReader.fixAircraftRoute(scenario, fixNumber);		
	}
	
	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, boolean isFractional) {
		buildNetwork(scenario, candidateAircraftList, candidateFlightList, 5);
		
		List<FlightSection> flightSectionList = new ArrayList<>();
		List<FlightSectionItinerary> flightSectionItineraryList = new ArrayList<>();
		
		for(Flight f:scenario.flightList) {
			flightSectionList.addAll(f.flightSectionList);
		}
		for(Itinerary ite:scenario.itineraryList) {
			flightSectionItineraryList.addAll(ite.flightSectionItineraryList);
		}
		
		
		
		//求解CPLEX模型
		//CplexModelForPureAircraft model = new CplexModelForPureAircraft();
		//Solution solution = model.run(candidateAircraftList, candidateFlightList, new ArrayList(), scenario.airportList,scenario, isFractional, true, false);		

		IntegratedCplexModel model = new IntegratedCplexModel();
		model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario.airportList, scenario, flightSectionList, scenario.itineraryList, flightSectionItineraryList, isFractional, true);

	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, int gap) {
		
		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructorBasedOnDelayAndEarlyLimit networkConstructorBasedOnDelayAndEarlyLimit = new NetworkConstructorBasedOnDelayAndEarlyLimit();

		int nnn1 = 0;
		int nnn2 = 0;
		int nnn3 = 0;
		int nnn4 = 0;
		for (Aircraft aircraft : candidateAircraftList) {	
			//System.out.println("one iteration : "+aircraft.id);
			List<FlightArc> totalFlightArcList = new ArrayList<>();
			List<ConnectingArc> totalConnectingArcList = new ArrayList<>();
			
			//System.out.println("aircraft:"+aircraft.id+"  "+aircraft.singleFlightList.size()+" "+aircraft.connectingFlightList.size());
			
			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
				//System.out.print(faList.size()+",");
			
			}
			//System.out.println();
	
			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				List<ConnectingArc> caList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForConnectingFlightPair(aircraft, cf, scenario);
				totalConnectingArcList.addAll(caList);
				//System.out.print(caList.size()+":");
			}
			//System.out.println();
			
			networkConstructorBasedOnDelayAndEarlyLimit.eliminateArcs(aircraft, scenario.airportList, totalFlightArcList, totalConnectingArcList, scenario);
		
			//System.out.println(totalFlightArcList.size()+" "+totalConnectingArcList.size()+"  "+aircraft.flightArcList.size()+"  "+aircraft.connectingArcList.size());
			nnn1 += totalFlightArcList.size();
			nnn2 += totalConnectingArcList.size();
			
			nnn3 += aircraft.flightArcList.size();
			nnn4 += aircraft.connectingArcList.size();
		}
		System.out.println("nnn:"+nnn1 +"  "+nnn2+"  "+nnn3+"  "+nnn4);
		
		ArcChecker.init();
		for(Aircraft a:candidateAircraftList) {
			System.out.println("aircraft : "+a.id);
			ArcChecker.checkFlightArcs(a, a.flightArcList);
		}
		
		System.exit(1);
		
		NetworkConstructor networkConstructor = new NetworkConstructor();
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
}
