package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;

import javax.swing.plaf.synth.SynthSpinnerUI;

import sghku.tianchi.IntelligentAviation.algorithm.FlightDelayLimitGenerator;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructorBasedOnDelayAndEarlyLimit;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.OutputResultWithPassenger;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.*;
import sghku.tianchi.IntelligentAviation.model.*;

public class SecondStagePassengerRecovery {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		Parameter.onlySignChangeDisruptedPassenger = false;  //只能转签受影响乘客

		Parameter.gap = 5;
		Parameter.stageIndex = 2;
		
		runOneIteration(false);

	}

	public static void runOneIteration(boolean isFractional){
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

			//Scanner sn = new Scanner(new File("delayfiles/linearsolution_30_421761.807_15.8.csv"));
			Scanner sn = new Scanner(new File("delayfiles/linearsolution_30_489295.42_967_largecancelcost.csv"));
		
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

				innerSn.next();
				innerSn.next();
				innerSn.next();
				innerSn.next();
				innerSn.next();

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

		List<Aircraft> candidateAircraftList = new ArrayList<>();
		for(int j=0;j<scenario.aircraftList.size();j++){
			Aircraft a = scenario.aircraftList.get(j);
			candidateAircraftList.add(a);
		}
		
		for(Aircraft a:candidateAircraftList){
			for(Flight f1:a.fixedFlightList){	
				
				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
					if(f1.isStraightened){
						f1.connectingFlightpair.firstFlight.isSelectedInSecondPhase = true;
						f1.connectingFlightpair.secondFlight.isSelectedInSecondPhase = true;
					}else{
						f1.isSelectedInSecondPhase = true;
					}
				}else{
					System.out.println("航班飞机匹配错误");
				}

				candidateFlightList.add(f1);
			}

			for(int i=0;i<a.fixedFlightList.size()-1;i++){
				Flight f1 = a.fixedFlightList.get(i);
				Flight f2 = a.fixedFlightList.get(i+1);
				
				f1.isShortConnection = false;
				f2.isShortConnection = false;

				Integer connT = scenario.shortConnectionMap.get(f1.id+"_"+f2.id);
				if(connT != null){
					f1.isShortConnection = true;
					f1.shortConnectionTime = connT;
				}

				if((f1.actualLandingT+(f1.isShortConnection?f1.shortConnectionTime:50)) > f2.actualTakeoffT){
					System.out.println("connection error  "+f1.actualLandingT+"  "+f2.actualTakeoffT+" "+f1.isIncludedInTimeWindow+" "+f2.isIncludedInTimeWindow+" "+f1.isShortConnection+" "+f1.shortConnectionTime+" "+f1.id+" "+f2.id+" "+f1.leg.destinationAirport.id);
				}

				if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id){

					ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id+"_"+f2.id);
					f1.isConnectionFeasible = true;
					f2.isConnectionFeasible = true;
					
					if(cf != null){
						a.connectingFlightList.add(cf);
						candidateConnectingFlightList.add(cf);
					}else{
						System.out.println("null connecting flight");
					}
				}
			}
		}

		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}

		//基于目前固定的飞机路径来进一步求解线性松弛模型
		//solver(scenario, scenario.aircraftList, candidateFlightList, candidateConnectingFlightList, isFractional);		
		solver(scenario, candidateAircraftList, scenario.flightList, new ArrayList(), isFractional);		

	}

	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, boolean isFractional) {
		
		//生成flight arc 和  node
		buildNetwork(scenario, candidateAircraftList, 5); 
		//生成FlightArcItinerary用来转签disrupted passengers
		constructFlightArcItinerary(scenario);
		//
		List<FlightArcItinerary> flightArcItineraryList = new ArrayList<>();
		
		for(Itinerary ite:scenario.itineraryList) {
			flightArcItineraryList.addAll(ite.flightArcItineraryList);
		}
		
		System.out.println("candidate:"+candidateFlightList.size()+" "+candidateConnectingFlightList.size());
		SecondStageCplexModel model = new SecondStageCplexModel();
		model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario,flightArcItineraryList,
				isFractional, false);

		OutputResultWithPassenger outputResultWithPassenger = new OutputResultWithPassenger();
		outputResultWithPassenger.writeResult(scenario, "firstresult825.csv");
	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, int gap) {
		// 为每一个飞机的网络模型生成arc
		NetworkConstructorBasedOnDelayAndEarlyLimit networkConstructorBasedOnDelayAndEarlyLimit = new NetworkConstructorBasedOnDelayAndEarlyLimit();

		for (Aircraft aircraft : candidateAircraftList) {	
			List<FlightArc> totalFlightArcList = new ArrayList<>();
			
			for (Flight f : aircraft.singleFlightList) {
				f.isFixed = false;
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructorBasedOnDelayAndEarlyLimit.generateArcForFlight(aircraft, f, scenario);
				totalFlightArcList.addAll(faList);
			}
			
			networkConstructorBasedOnDelayAndEarlyLimit.eliminateArcs(aircraft, scenario.airportList, totalFlightArcList, new ArrayList<>(), scenario);
		}

		NetworkConstructor networkConstructor = new NetworkConstructor();
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	
	
		/*NetworkConstructor networkConstructor = new NetworkConstructor();

		for (Aircraft aircraft : candidateAircraftList) {	
			
			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, 5, scenario);
			}

			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				List<ConnectingArc> caList = networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, 5, false, scenario);
			}
		}
		
		
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);*/
	}

	//计算itinerary，和FlightArcItinerary
	public static void constructFlightArcItinerary(Scenario sce) {
		/*// clear scenario 里面储存的上阶段用的itinerary
		sce.itineraryList.clear();

		// 检测每一个flight的（可被签转的）disrupted passenger, itinerary只包含可被签转的disrupted passenger
		for (Flight f : sce.flightList) {
			//只考虑time_window内
			if (f.isIncludedInTimeWindow) {
				int capacity = f.aircraft.passengerCapacity;  //分配给flight的aircraft的容量
				if(f.isCancelled || f.isStraightened){       //如果cancel或者straighten，normal passenger不能再坐这个航班了，所以capacity设为0
					capacity = 0;
				}
				int totalVolume = 0;    

				if(f.isIncludedInConnecting && !f.brotherFlight.isCancelled) {  //联程航班另一截没被cancel，要给connecting passenger留座
					totalVolume = f.connectedPassengerNumber + f.passengerNumber;
				}else {
					totalVolume = f.passengerNumber;
				}

				int cancelNum = Math.min(Math.max(0, totalVolume-capacity), f.normalPassengerNumber);  //只签转普通乘客

				if(cancelNum > 0){
					Itinerary ite = new Itinerary();
					ite.flight = f;
					ite.volume = cancelNum;

					f.itinerary = ite;

					sce.itineraryList.add(ite);
				}					
			}
		}

		// 为每一个行程检测可以替代的航班
		for (Itinerary ite :sce.itineraryList) {
			for (Flight f : sce.flightList) {    //不考虑connectingflight，因其不能承载转签乘客
				//在time_window内，起始、到达机场相同，不是原来的航班，没被cancel
				if (f.isIncludedInTimeWindow && f.leg.equals(ite.flight.leg) && f.id != ite.flight.id && !f.isCancelled) {
					// 判断时间上是够可以承载该行程
					int earliestT = f.initialTakeoffT - (f.isAllowtoBringForward ? Parameter.MAX_LEAD_TIME : 0);
					int latestT = f.initialTakeoffT + (f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);

					if(latestT >= ite.flight.initialTakeoffT && earliestT <= ite.flight.initialTakeoffT + 48*60){
						ite.candidateFlightList.add(f);
					}
				}
			}		
		}
*/
		//生成FlightArcItinerary
		int way = 1;
		for (Itinerary ite : sce.itineraryList) {
			// 生成替代航班相关的FlightArcItinerary
			for (Flight f : ite.candidateFlightList) {
				for (FlightArc fa : f.flightarcList) {
					int tkfTime = fa.takeoffTime;

					FlightArcItinerary fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = fa;
					fai.unitCost = -1;
					int delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(way == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay < 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}		
					}

					if (fai.unitCost > 0-1e-5) {
						fai.unitCost = fai.unitCost;
						ite.flightArcItineraryList.add(fai);
						fa.flightArcItineraryList.add(fai);
					}
				}
				
				
				for (ConnectingArc ca : f.connectingarcList) {
					
					//connecting arc's first arc
					int tkfTime = ca.firstArc.takeoffTime;
					
					FlightArcItinerary fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = ca.firstArc;
					fai.unitCost = -1;
					
					int delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(way == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay < 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}						
					}

					if (fai.unitCost > 0-1e-5) {
						fai.unitCost = fai.unitCost;
						ite.flightArcItineraryList.add(fai);
						ca.firstArc.flightArcItineraryList.add(fai);
					}
					
					//connecting arc's second arc
					tkfTime = ca.secondArc.takeoffTime;
					
					fai = new FlightArcItinerary();
					fai.itinerary = ite;
					fai.flightArc = ca.secondArc;
					fai.unitCost = -1;
					
					delay = tkfTime - ite.flight.initialTakeoffT;  //in minute

					if (delay >= 0) {
						if(way == 1){
							if (delay < 6 * 60) {
								fai.unitCost = delay/(60.0*30.0);   //if delay 5 minutes, cost = 0.0027
							} else if (delay >= 6 * 60 && delay < 24 * 60) {
								fai.unitCost = delay/(60.0*24.0); 
							} else if (delay >= 24 * 60 && delay < 36 * 60) {
								fai.unitCost = delay/(60.0*18.0); 
							} else if (delay >= 36 * 60 && delay <= 48 * 60) {
								fai.unitCost = delay/(60.0*16.0);
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}else{
							if (delay < 6 * 60) {
								fai.unitCost = 0;
							} else if (delay < 24 * 60 && delay >= 6 * 60) {
								fai.unitCost = 0.5;
							} else if (delay < 48 * 60 && delay >= 24 * 60) {
								fai.unitCost = 1;
							} else {
								if(delay < 48*60){
									System.out.println("error delay:" + delay);								
								}
							}
						}						
					}

					if (fai.unitCost > 0-1e-5) {
						fai.unitCost = fai.unitCost;
						ite.flightArcItineraryList.add(fai);
						ca.secondArc.flightArcItineraryList.add(fai);
					}
				}
			}
		}


	}

}
