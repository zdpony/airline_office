package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.ArrayList;
import java.util.List;

import sghku.tianchi.IntelligentAviation.clique.Clique;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class NetworkBuilder {
	public Scenario scenario = null;
	public int gap;
	
	public NetworkBuilder(Scenario scenario, int gap){
		this.scenario = scenario;
		this.gap = gap;
	}
	
	// 初始化
	public void init() {
		for (Aircraft a : scenario.aircraftList) {
			a.init();
		}
		for (Flight f : scenario.flightList) {
			f.init();
		}
		for (Airport airport : scenario.airportList) {
			airport.init();
		}
	}

	// 构建时空网络流模型
	public void buildNetwork(Clique clique, boolean isDeadheadAllowed, boolean isStraightenAllowed,
			boolean isInsertCancelledFlights, boolean isGenerateArcForSingleFlightInConnectingFlightPair) {
		List<Aircraft> smallAircraftList = clique.aircraftList;

		// 先初始化所有的信息
		init();

		// 计算每一个机场最终需要的飞机数量
		for (Airport airport : scenario.airportList) {
			for(int i=0;i<Parameter.TOTAL_AIRCRAFTTYPE_NUM;i++) {
				airport.finalAircraftNumber[i] = 0;
			}
		}
		for (Aircraft a : smallAircraftList) {
			Airport destAirport = null;
			if (a.flightList.size() > 0) {
				destAirport = a.flightList.get(a.flightList.size() - 1).leg.destinationAirport;
			} else {
				destAirport = a.initialLocation;
			}
			destAirport.finalAircraftNumber[a.type-1]++;
		}

		// 计算当前问题需要考虑的航班
		for (Aircraft a : smallAircraftList) {
			int currentIndex = 0;

			while (currentIndex < a.flightList.size()) {
				Flight f1 = a.flightList.get(currentIndex);

				if (f1.isDeadhead) {
					// 调剂航班为飞有效航班，不需要加入到有效航班序列中
					for (Aircraft b : smallAircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.deadheadFlightList.add(f1);
						}
					}
					currentIndex++;
				} else if (f1.isStraightened) {
					// 联程拉直航班需要将其中两段都加入到有效航班序列中
					clique.realFlightList.add(f1.connectingFlightpair.firstFlight);
					clique.realFlightList.add(f1.connectingFlightpair.secondFlight);

					for (Aircraft b : smallAircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.straightenedFlightList.add(f1);
						}
					}

					currentIndex++;
				} else {
					// 该航班是正常航班,判断该航班是否包含在某一个联程航班内

					if (currentIndex == a.flightList.size() - 1) {
						clique.realFlightList.add(f1);

						for (Aircraft b : smallAircraftList) {
							if (!b.checkFlyViolation(f1)) {
								b.singleFlightList.add(f1);
							}
						}
						currentIndex++;
					} else {
						if (!f1.isIncludedInConnecting) {

							clique.realFlightList.add(f1);

							for (Aircraft b : smallAircraftList) {
								if (!b.checkFlyViolation(f1)) {
									b.singleFlightList.add(f1);
								}
							}
							currentIndex++;
						} else {
							Flight f2 = a.flightList.get(currentIndex + 1);
							if (f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id) {
								// if(f2.isIncludedInConnecting && f1.flightNo
								// == f2.flightNo){
								ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id + "_" + f2.id);

								if (cf == null) {
									System.out.println("we find this error " + f1.id + " " + f2.id);
									System.exit(1);
								}

								clique.realFlightList.add(f1);
								clique.realFlightList.add(f2);

								clique.realConnectingFlightPairList.add(cf);

								for (Aircraft b : smallAircraftList) {
									if (!b.checkFlyViolation(cf)) {
										b.connectingFlightList.add(cf);
									}
								}

								currentIndex += 2;
							} else {
								clique.realFlightList.add(f1);

								for (Aircraft b : smallAircraftList) {
									if (!b.checkFlyViolation(f1)) {
										b.singleFlightList.add(f1);
									}
								}
								currentIndex++;
							}
						}
					}
				}
			}

		}

		// 如果需要插入取消航班
		if (isInsertCancelledFlights) {
			for (ConnectingFlightpair cf : scenario.connectingFlightList) {
				if (cf.firstFlight.isCancelled && cf.secondFlight.isCancelled) {

					clique.realFlightList.add(cf.firstFlight);
					clique.realFlightList.add(cf.secondFlight);

					clique.realConnectingFlightPairList.add(cf);

					for (Aircraft b : smallAircraftList) {
						if (!b.checkFlyViolation(cf)) {
							b.connectingFlightList.add(cf);
						}
					}
				}
			}

			for (Flight f : scenario.flightList) {
				if (!f.isIncludedInConnecting) {
					if (f.isCancelled) {
						clique.realFlightList.add(f);

						for (Aircraft b : smallAircraftList) {
							if (!b.checkFlyViolation(f)) {
								b.singleFlightList.add(f);
							}
						}
					}
				}
			}
		}

		// 生成调机航班
		if (isDeadheadAllowed) {
			for (Aircraft targetA : smallAircraftList) {
				for (Aircraft a : smallAircraftList) {
					targetA.deadheadFlightList.addAll(targetA.generateDeadheadFlight(scenario.legList, a.flightList));
				}
			}
		}

		// 生成联程拉直航班
		if (isStraightenAllowed) {
			for (int i = 0; i < smallAircraftList.size(); i++) {
				Aircraft targetA = smallAircraftList.get(i);

				for (ConnectingFlightpair cp : targetA.connectingFlightList) {
					Flight straightenedFlight = targetA.generateStraightenedFlight(cp);
					if (straightenedFlight != null) {
						targetA.straightenedFlightList.add(straightenedFlight);
					}
				}
			}
		}

		// 每一个航班生成arc

		// 为每一个飞机的网络模型生成arc
		NetworkConstructor networkConstructor = new NetworkConstructor();
		for (Aircraft aircraft : smallAircraftList) {
			for (Flight f : aircraft.singleFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap);
			}

			for (Flight f : aircraft.straightenedFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap);
			}
			
			for (Flight f : aircraft.deadheadFlightList) {
				networkConstructor.generateArcForFlight(aircraft, f, gap);
			}

			for (ConnectingFlightpair cf : aircraft.connectingFlightList) {
				networkConstructor.generateArcForConnectingFlightPair(aircraft, cf, gap,
						isGenerateArcForSingleFlightInConnectingFlightPair);
			}
		}

		networkConstructor.generateNodes(smallAircraftList, scenario.airportList);
	}

	// 构建时空网络流模型
	public void buildNetworkInThirdPhase(Clique clique, boolean isInsertCancelledFlights,
			boolean isGenerateArcForSingleFlightInConnectingFlightPair) {

		// 先初始化所有的信息
		init();

		// 计算每一个机场最终需要的飞机数量
		for (Airport airport : scenario.airportList) {
			for(int i=0;i<Parameter.TOTAL_AIRCRAFTTYPE_NUM;i++) {
				airport.finalAircraftNumber[i] = 0;				
			}
		}
		for (Aircraft a : scenario.aircraftList) {
			Airport destAirport = null;
			if (a.flightList.size() > 0) {
				destAirport = a.flightList.get(a.flightList.size() - 1).leg.destinationAirport;
			} else {
				destAirport = a.initialLocation;
			}
			destAirport.finalAircraftNumber[a.type-1]++;
		}

		// 计算当前问题需要考虑的航班
		for (Aircraft a : clique.aircraftList) {
			int currentIndex = 0;

			while (currentIndex < a.flightList.size()) {
				Flight f1 = a.flightList.get(currentIndex);

				if (f1.isDeadhead) {
					// 调剂航班为飞有效航班，不需要加入到有效航班序列中
					for (Aircraft b : clique.aircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.deadheadFlightList.add(f1);
						}
					}

					for (Aircraft b : clique.candidateAircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.deadheadFlightList.add(f1);
						}
					}
					currentIndex++;
				} else if (f1.isStraightened) {
					// 联程拉直航班需要将其中两段都加入到有效航班序列中
					clique.realFlightList.add(f1.connectingFlightpair.firstFlight);
					clique.realFlightList.add(f1.connectingFlightpair.secondFlight);

					for (Aircraft b : clique.aircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.straightenedFlightList.add(f1);
						}
					}

					for (Aircraft b : clique.candidateAircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.straightenedFlightList.add(f1);
						}
					}

					currentIndex++;
				} else {
					// 该航班是正常航班,判断该航班是否包含在某一个联程航班内

					if (currentIndex == a.flightList.size() - 1) {
						clique.realFlightList.add(f1);

						for (Aircraft b : clique.aircraftList) {
							if (!b.checkFlyViolation(f1)) {
								b.singleFlightList.add(f1);
							}
						}
						for (Aircraft b : clique.candidateAircraftList) {
							if (!b.checkFlyViolation(f1)) {
								b.singleFlightList.add(f1);
							}
						}
						currentIndex++;
					} else {
						if (!f1.isIncludedInConnecting) {

							clique.realFlightList.add(f1);

							for (Aircraft b : clique.aircraftList) {
								if (!b.checkFlyViolation(f1)) {
									b.singleFlightList.add(f1);
								}
							}
							for (Aircraft b : clique.candidateAircraftList) {
								if (!b.checkFlyViolation(f1)) {
									b.singleFlightList.add(f1);
								}
							}
							currentIndex++;
						} else {
							Flight f2 = a.flightList.get(currentIndex + 1);
							if (f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id) {
								// if(f2.isIncludedInConnecting && f1.flightNo
								// == f2.flightNo){
								ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id + "_" + f2.id);

								if (cf == null) {
									System.out.println("we find this error " + f1.id + " " + f2.id);
									System.exit(1);
								}

								clique.realFlightList.add(f1);
								clique.realFlightList.add(f2);

								clique.realConnectingFlightPairList.add(cf);

								for (Aircraft b : clique.aircraftList) {
									if (!b.checkFlyViolation(cf)) {
										b.connectingFlightList.add(cf);
									}
								}

								for (Aircraft b : clique.candidateAircraftList) {
									if (!b.checkFlyViolation(cf)) {
										b.connectingFlightList.add(cf);
									}
								}

								currentIndex += 2;
							} else {
								clique.realFlightList.add(f1);

								for (Aircraft b : clique.aircraftList) {
									if (!b.checkFlyViolation(f1)) {
										b.singleFlightList.add(f1);
									}
								}

								for (Aircraft b : clique.candidateAircraftList) {
									if (!b.checkFlyViolation(f1)) {
										b.singleFlightList.add(f1);
									}
								}
								currentIndex++;
							}
						}
					}
				}
			}
		}

		// 计算当前问题candidate aircraft list
		for (Aircraft a : clique.candidateAircraftList) {
			int currentIndex = 0;

			while (currentIndex < a.flightList.size()) {
				Flight f1 = a.flightList.get(currentIndex);

				if (f1.isDeadhead) {
					// 调剂航班为飞有效航班，不需要加入到有效航班序列中
					if (!a.checkFlyViolation(f1)) {
						a.deadheadFlightList.add(f1);
					}

					for (Aircraft b : clique.aircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.deadheadFlightList.add(f1);
						}
					}

					currentIndex++;
				} else if (f1.isStraightened) {
					// 联程拉直航班需要将其中两段都加入到有效航班序列中
					clique.realFlightList.add(f1.connectingFlightpair.firstFlight);
					clique.realFlightList.add(f1.connectingFlightpair.secondFlight);

					if (!a.checkFlyViolation(f1)) {
						a.straightenedFlightList.add(f1);
					}

					for (Aircraft b : clique.aircraftList) {
						if (!b.checkFlyViolation(f1)) {
							b.straightenedFlightList.add(f1);
						}
					}

					currentIndex++;
				} else {
					// 该航班是正常航班,判断该航班是否包含在某一个联程航班内

					if (currentIndex == a.flightList.size() - 1) {
						clique.realFlightList.add(f1);

						if (!a.checkFlyViolation(f1)) {
							a.singleFlightList.add(f1);
						}

						for (Aircraft b : clique.aircraftList) {
							if (!b.checkFlyViolation(f1)) {
								b.singleFlightList.add(f1);
							}
						}

						currentIndex++;
					} else {
						if (!f1.isIncludedInConnecting) {

							clique.realFlightList.add(f1);

							if (!a.checkFlyViolation(f1)) {
								a.singleFlightList.add(f1);
							}

							for (Aircraft b : clique.aircraftList) {
								if (!b.checkFlyViolation(f1)) {
									b.singleFlightList.add(f1);
								}
							}

							currentIndex++;
						} else {
							Flight f2 = a.flightList.get(currentIndex + 1);
							if (f2.isIncludedInConnecting && f1.brotherFlight.id == f2.id) {
								// if(f2.isIncludedInConnecting && f1.flightNo
								// == f2.flightNo){
								ConnectingFlightpair cf = scenario.connectingFlightMap.get(f1.id + "_" + f2.id);

								if (cf == null) {
									System.out.println("we find this error " + f1.id + " " + f2.id);
									System.exit(1);
								}

								clique.realFlightList.add(f1);
								clique.realFlightList.add(f2);

								clique.realConnectingFlightPairList.add(cf);

								if (!a.checkFlyViolation(cf)) {
									a.connectingFlightList.add(cf);
								}

								for (Aircraft b : clique.aircraftList) {
									if (!b.checkFlyViolation(cf)) {
										b.connectingFlightList.add(cf);
									}
								}

								currentIndex += 2;
							} else {
								clique.realFlightList.add(f1);

								if (!a.checkFlyViolation(f1)) {
									a.singleFlightList.add(f1);
								}

								for (Aircraft b : clique.aircraftList) {
									if (!b.checkFlyViolation(f1)) {
										b.singleFlightList.add(f1);
									}
								}

								currentIndex++;
							}
						}
					}
				}
			}
		}

		// 如果需要插入取消航班
		if (isInsertCancelledFlights) {
			for (ConnectingFlightpair cf : scenario.connectingFlightList) {
				if (cf.firstFlight.isCancelled && cf.secondFlight.isCancelled) {

					clique.realFlightList.add(cf.firstFlight);
					clique.realFlightList.add(cf.secondFlight);

					clique.realConnectingFlightPairList.add(cf);

					for (Aircraft b : clique.aircraftList) {
						if (!b.checkFlyViolation(cf)) {
							b.connectingFlightList.add(cf);
						}
					}

					for (Aircraft b : clique.candidateAircraftList) {
						if (!b.checkFlyViolation(cf)) {
							b.connectingFlightList.add(cf);
						}
					}
				}
			}

			for (Flight f : scenario.flightList) {
				if (!f.isIncludedInConnecting) {
					if (f.isCancelled) {
						clique.realFlightList.add(f);

						for (Aircraft b : clique.aircraftList) {
							if (!b.checkFlyViolation(f)) {
								b.singleFlightList.add(f);
							}
						}

						for (Aircraft b : clique.candidateAircraftList) {
							if (!b.checkFlyViolation(f)) {
								b.singleFlightList.add(f);
							}
						}
					}
				}
			}
		}

		// 为每一个飞机的网络模型生成arc
		NetworkConstructor networkConstructor = new NetworkConstructor();
		List<Aircraft> smallAircraftList = new ArrayList<>();
		smallAircraftList.addAll(clique.aircraftList);
		smallAircraftList.addAll(clique.candidateAircraftList);

		for (Aircraft aircraft : smallAircraftList) {
			for (Flight f : aircraft.singleFlightList) {
				networkConstructor.generateArcForFlightBasedOnReference(aircraft, f);
			}

			for (Flight f : aircraft.straightenedFlightList) {
				networkConstructor.generateArcForFlightBasedOnReference(aircraft, f);
			}
			for (Flight f : aircraft.deadheadFlightList) {
				networkConstructor.generateArcForFlightBasedOnReference(aircraft, f);
			}

			for (ConnectingFlightpair cf : aircraft.connectingFlightList) {
				networkConstructor.generateArcForConnectingFlightPairBasedOnReference(aircraft, cf,
						isGenerateArcForSingleFlightInConnectingFlightPair);
			}
		}

		networkConstructor.generateNodes(smallAircraftList, scenario.airportList);
	}
}
