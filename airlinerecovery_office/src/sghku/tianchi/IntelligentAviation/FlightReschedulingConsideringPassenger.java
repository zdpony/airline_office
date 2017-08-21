package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
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
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class FlightReschedulingConsideringPassenger {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		
		runOneIteration(false);
		
	}
		
	public static void runOneIteration(boolean isFractional){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);
		System.out.println("---------------this way ---------");
		
		//1.初始化，所有的航班取消
		for(Flight f:scenario.flightList){
			f.isCancelled = true;
			f.aircraft = f.initialAircraft;
			f.actualTakeoffT = f.initialTakeoffT;
			f.actualLandingT = f.initialLandingT;
		}
		
		AircraftPathReader scheduleReader = new AircraftPathReader();
		
		//读取已经固定的飞机路径
		Scanner sn = null;
		try {
			sn = new Scanner(new File("fixschedule"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while(sn.hasNextLine()) {
			String nextLine = sn.nextLine().trim();
			
			if(nextLine.equals("")) {
				break;
			}
			scheduleReader.read(nextLine, scenario);
		}
		
		List<Flight> candidateFlightList = new ArrayList<>();
		List<ConnectingFlightpair> candidateConnectingFlightList = new ArrayList<>();
		
		int nnn = 0;
		for(Aircraft a:scenario.aircraftList){
			
			nnn += a.fixedFlightList.size();
			
			for(Flight f1:a.fixedFlightList){
				
				//单独处理联程拉直航班
				if(!f1.actualDestination.equals(f1.leg.destinationAirport)){
					f1.isStraightened = true;
					f1.connectingFlightpair = f1.connectingFlightpair;
					f1.leg = f1.connectingFlightpair.straightenLeg;
					
					f1.flyTime = f1.actualLandingT-f1.actualTakeoffT;
												
					f1.initialLandingT = f1.initialTakeoffT + f1.flyTime;
					
					f1.connectingFlightpair.secondFlight.isStraightened = true;
					System.out.println("one straightened flight");
				}
				
				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
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
				
				if(!f1.isStraightened && !f2.isStraightened) {
					Integer connT = scenario.shortConnectionMap.get(f1.id+"_"+f2.id);
					if(connT != null) {
						f1.isShortConnection = true;
						f1.shortConnectionTime = connT;
					}
				}
				if((f1.actualLandingT+(f1.isShortConnection?f1.shortConnectionTime:50)) > f2.actualTakeoffT){
					System.out.println("connection error  "+f1.actualLandingT+"  "+f2.actualTakeoffT+" "+f1.isIncludedInTimeWindow+" "+f2.isIncludedInTimeWindow+" "+f1.isShortConnection+" "+f1.shortConnectionTime+" "+f1.id+" "+f2.id);
				}
				
				if(f1.isIncludedInConnecting && f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id){
					ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id+"_"+f2.id);
					
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
		solver(scenario, scenario.aircraftList, candidateFlightList, isFractional);
			
	}
	
	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, boolean isFractional) {
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

		CplexModel model = new CplexModel();
		model.run(candidateAircraftList, candidateFlightList, new ArrayList(), scenario.airportList, scenario, flightSectionList, scenario.itineraryList, flightSectionItineraryList, isFractional, true, false);

		OutputResultWithPassenger outputResultWithPassenger = new OutputResultWithPassenger();
		outputResultWithPassenger.writeResult(scenario, "firstresult821.csv");
	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, int gap) {
	
		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructor networkConstructor = new NetworkConstructor();
		for (Aircraft aircraft : candidateAircraftList) {	
			for (Flight f : aircraft.singleFlightList) {
				//List<FlightArc> faList = networkConstructor.generateArcForFlightBasedOnFixedSchedule(aircraft, f, scenario);
				List<FlightArc> faList = networkConstructor.generateArcForFlight(aircraft, f, 5, scenario);
			}
			for(ConnectingFlightpair cf:aircraft.connectingFlightList){
				List<ConnectingArc> caList = networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, 5, false, scenario);
			}
		}
		
		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
}