package sghku.tianchi.IntelligentAviation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.algorithm.NetworkConstructor;
import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.OutputResult;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;
import sghku.tianchi.IntelligentAviation.model.CplexModel;
import sghku.tianchi.IntelligentAviation.model.CplexModelForPureAircraft;
import sghku.tianchi.IntelligentAviation.model.PushForwardCplexModel;

public class LinearRecoveryModelWithPartialFixed {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = false;
		
		//前面两个循环求解线性松弛模型
		runOneIteration(70,true);
		runOneIteration(40, true);
		//最后一个循环直接解整数规划模型
		runOneIteration(32, false);
		
		//将航班时刻尽可能往前推进
		//pushforward();
	}
		
	public static void runOneIteration(int fixNumber, boolean isFractional){
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);

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
		
		List<Aircraft> candidateAircraftList= new ArrayList<>();
		
		for(Flight f:scenario.flightList) {
			if(!f.isFixed) {
				if(f.isIncludedInConnecting) {
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
		for(Aircraft a:scenario.aircraftList) {
			if(!a.isFixed) {
				candidateAircraftList.add(a);
			}
		}
		
		System.out.println("candidate:"+candidateAircraftList.size()+" "+candidateFlightList.size()+" "+candidateConnectingFlightList.size());
		

		//更新base information
		for(Aircraft a:scenario.aircraftList) {
			a.flightList.get(a.flightList.size()-1).leg.destinationAirport.finalAircraftNumber[a.type-1]++;
		}
		for(Aircraft a:scenario.aircraftList) {
			if(a.isFixed) {
				
				a.fixedDestination.finalAircraftNumber[a.type-1]--;
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
		scheduleReader.fixAircraftRoute(scenario, fixNumber);		
	}
	
	//求解线性松弛模型或者整数规划模型
	public static void solver(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, boolean isFractional) {
		buildNetwork(scenario, candidateAircraftList, candidateFlightList, candidateConnectingFlightList, 60);
		
		//求解CPLEX模型
		CplexModelForPureAircraft model = new CplexModelForPureAircraft();
		Solution solution = model.run(candidateAircraftList, candidateFlightList, candidateConnectingFlightList, scenario.airportList,scenario, isFractional, true);		
	}

	// 构建时空网络流模型
	public static void buildNetwork(Scenario scenario, List<Aircraft> candidateAircraftList, List<Flight> candidateFlightList, List<ConnectingFlightpair> candidateConnectingFlightList, int gap) {
	

		// 计算当前问题需要考虑的航班
		for (Aircraft a : candidateAircraftList) {
			
			for(Flight f1:candidateFlightList) {
				if (!a.checkFlyViolation(f1)) {
					a.singleFlightList.add(f1);
				}
			}
			
			
			for(ConnectingFlightpair cf:candidateConnectingFlightList) {
				if (!a.checkFlyViolation(cf)) {
					a.connectingFlightList.add(cf);
				}
			}
		}

		
		// 生成联程拉直航班
		for (int i = 0; i < candidateAircraftList.size(); i++) {
			Aircraft targetA = candidateAircraftList.get(i);

			for (ConnectingFlightpair cp : targetA.connectingFlightList) {
				Flight straightenedFlight = targetA.generateStraightenedFlight(cp);
				if (straightenedFlight != null) {
					targetA.straightenedFlightList.add(straightenedFlight);
				}
			}
		}

		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructor networkConstructor = new NetworkConstructor();
		for (Aircraft aircraft : candidateAircraftList) {
			for (Flight f : aircraft.singleFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);
			}

			for (Flight f : aircraft.straightenedFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);
			}
			for (Flight f : aircraft.deadheadFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap, scenario);
			}

			for (ConnectingFlightpair cf : aircraft.connectingFlightList) {
				networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, gap,
						true, scenario);
			}
		}

		networkConstructor.generateNodes(candidateAircraftList, scenario.airportList, scenario);
	}
	
	/*//将航班时刻尽量往前推
	public static void pushforward() {
		
		Scenario scenario = new Scenario(Parameter.EXCEL_FILENAME);

		//RecoverySolver solver = new RecoverySolver(scenario);

		// 读取初始解
		ScheduleReader scheduleReader = new ScheduleReader();
		scheduleReader.readV2(scenario);

		for (Flight f : scenario.flightList) {
			f.isCancelled = true;
		}
		for (Aircraft a : scenario.aircraftList) {
			for (Flight f : a.flightList) {
				if (f.isStraightened) {
					f.connectingFlightpair.firstFlight.isCancelled = false;
					f.connectingFlightpair.secondFlight.isCancelled = false;
				} else if (f.isDeadhead) {

				} else {
					f.isCancelled = false;
				}
			}
		}

		PushForwardCplexModel pushforward = new PushForwardCplexModel();
		pushforward.run(scenario.aircraftList);

		// 根据最终信息更新航班的信息
		solver.updateFlightList();

		String outputName = "result/sghku_20170715_" + Parameter.fileIndex + ".csv";
		Parameter.fileIndex++;

		OutputResult opr = new OutputResult();
		opr.writeResult(scenario, outputName);

		File fff = new File("current.csv");
		if (fff.exists()) {
			fff.delete();
		}
		outputName = "current.csv";
		opr.writeResult(scenario, outputName);

		long endTime = System.currentTimeMillis();
		System.out.println(
				"computation time: " + 0 + " obj:" + scenario.calculateObj());

	}*/
}
