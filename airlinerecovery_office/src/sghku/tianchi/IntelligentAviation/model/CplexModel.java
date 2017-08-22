package sghku.tianchi.IntelligentAviation.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ilog.concert.IloException;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Param.MIP.Strategy;
import sghku.tianchi.IntelligentAviation.common.MyFile;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator2;
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
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.PassengerTransfer;
import sghku.tianchi.IntelligentAviation.entity.Path;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.Solution;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

public class CplexModel {
	public IloCplex cplex;

	//the network flow model for initial problem solving

	public Solution run(List<Aircraft> aircraftList, List<Flight> flightList, List<ConnectingFlightpair> cfList, List<Airport> airportList, Scenario sce, List<FlightSection> flightSectionList, List<Itinerary> itineraryList, List<FlightSectionItinerary> flightSectionItineraryList, boolean isFractional, boolean isAllowToCancelSingleFlightInAnConnectingFlight, boolean isCancelAllowed){
		Solution solution = new Solution();
		solution.involvedAircraftList.addAll(aircraftList);
		
		try {
				
			cplex = new IloCplex();
			
			if(isFractional){
				
			}else{
				//cplex.setOut(null);				
			}

			List<FlightArc> flightArcList = new ArrayList<>();
			List<ConnectingArc> connectingArcList = new ArrayList<>();
			List<GroundArc> groundArcList = new ArrayList<>();
			List<Node> nodeList = new ArrayList<>();

			for(Aircraft aircraft:aircraftList){
				flightArcList.addAll(aircraft.flightArcList);
				connectingArcList.addAll(aircraft.connectingArcList);
				groundArcList.addAll(aircraft.groundArcList);

				for(int i=0;i<Parameter.TOTAL_AIRPORT_NUM;i++){
					nodeList.addAll(aircraft.nodeListArray[i]);
				}

				nodeList.add(aircraft.sourceNode);
				nodeList.add(aircraft.sinkNode);
			}

			System.out.println("start solving:"+flightArcList.size()+" "+connectingArcList.size()+" "+groundArcList.size()+" "+nodeList.size()+" "+ flightList.size());
			
			
			IloNumVar[] x = new IloNumVar[flightArcList.size()];
			IloNumVar[] beta = new IloNumVar[connectingArcList.size()];
			IloNumVar[] y = new IloNumVar[groundArcList.size()];
			IloNumVar[] z = new IloNumVar[flightList.size()];
			
			IloNumVar[] passX = new IloNumVar[flightSectionItineraryList.size()];
			IloNumVar[] passCancel = new IloNumVar[itineraryList.size()];
			//IloNumVar[] gamma = new IloNumVar[airportList.size()];

			IloLinearNumExpr obj = cplex.linearNumExpr();

			for(int i=0;i<flightArcList.size();i++){
				if(isFractional){
					x[i] = cplex.numVar(0, 1);
				}else{
					x[i] = cplex.boolVar();
				}
				
				obj.addTerm(flightArcList.get(i).cost, x[i]);
				flightArcList.get(i).id = i;
			}
			for(int i=0;i<connectingArcList.size();i++){
				if(isFractional){
					beta[i] = cplex.numVar(0, 1);
				}else{
					beta[i] = cplex.boolVar();					
				}
				obj.addTerm(connectingArcList.get(i).cost, beta[i]);
				connectingArcList.get(i).id = i;
			}
			for(int i=0;i<groundArcList.size();i++){
				//y[i] = cplex.boolVar();
				y[i] = cplex.numVar(0, 1);
				groundArcList.get(i).id = i;
			}
			for(int i=0;i<flightList.size();i++){
				Flight f = flightList.get(i);
				//z[i] = cplex.boolVar();
				f.IDInCPLEXModel = i;
				z[i] = cplex.numVar(0, 1);

				if(Parameter.isPassengerCostConsidered){
					obj.addTerm(f.importance*Parameter.COST_CANCEL+f.totalConnectingCancellationCost, z[i]);					
				}else{
					obj.addTerm(f.importance*Parameter.COST_CANCEL, z[i]);
				}			
			}
			
			for(int i=0;i<flightSectionItineraryList.size();i++) {
				FlightSectionItinerary fsi = flightSectionItineraryList.get(i);
				fsi.id = i;	 
				//passX[i] = cplex.numVar(0, fsi.itinerary.volume);
				passX[i] = cplex.intVar(0, fsi.itinerary.volume);
				
				obj.addTerm(fsi.unitCost, passX[i]);
			}
			for(int i=0;i<itineraryList.size();i++) {
				Itinerary ite = itineraryList.get(i);
				ite.id = i;
				passCancel[i] = cplex.numVar(0, ite.volume);
				obj.addTerm(Parameter.passengerCancelCost, passCancel[i]);
			}
					
			cplex.addMinimize(obj);

			//1. flow balance constraints
			for(Node n:nodeList){
				IloLinearNumExpr flowBalanceConstraint = cplex.linearNumExpr();

				for(FlightArc arc:n.flowinFlightArcList){
					flowBalanceConstraint.addTerm(1, x[arc.id]);
				}
				for(FlightArc arc:n.flowoutFlightArcList){
					flowBalanceConstraint.addTerm(-1, x[arc.id]);
				}

				for(ConnectingArc arc:n.flowinConnectingArcList){
					flowBalanceConstraint.addTerm(1, beta[arc.id]);
				}
				for(ConnectingArc arc:n.flowoutConnectingArcList){
					flowBalanceConstraint.addTerm(-1, beta[arc.id]);
				}

				for(GroundArc arc:n.flowinGroundArcList){
					flowBalanceConstraint.addTerm(1, y[arc.id]);
				}
				for(GroundArc arc:n.flowoutGroundArcList){
					flowBalanceConstraint.addTerm(-1, y[arc.id]);
				}

				cplex.addEq(flowBalanceConstraint, 0);
			}

			//2. turn-around arc flow
			for(Aircraft aircraft:aircraftList){
				IloLinearNumExpr turnaroundConstraint = cplex.linearNumExpr();

				turnaroundConstraint.addTerm(1, y[aircraft.turnaroundArc.id]);

				cplex.addEq(turnaroundConstraint, 1);
			}

			//3. for each flight, at least one arc can be selected, the last flight can not be cancelled
			for(int i=0;i<flightList.size();i++){
				Flight f = flightList.get(i);

				IloLinearNumExpr flightSelectionConstraint = cplex.linearNumExpr();

				for(FlightArc arc:f.flightarcList){
					flightSelectionConstraint.addTerm(1, x[arc.id]);
				}

				for(ConnectingArc arc:f.connectingarcList){
					flightSelectionConstraint.addTerm(1, beta[arc.id]);
				}

				/*if(!f.isLatest){
					flightSelectionConstraint.addTerm(1, z[i]);
				}*/
				if(isCancelAllowed){
					if(f.isIncludedInTimeWindow){
						flightSelectionConstraint.addTerm(1, z[i]);	
					}
				}

				cplex.addEq(flightSelectionConstraint, 1);
			}

			//4. base balance constraints
			for(int i=0;i<airportList.size();i++){
				Airport airport = airportList.get(i);

				for(int j=0;j<Parameter.TOTAL_AIRCRAFTTYPE_NUM;j++) {
					IloLinearNumExpr baseConstraint = cplex.linearNumExpr();
					for(GroundArc arc:airport.sinkArcList[j]){
		
						baseConstraint.addTerm(1, y[arc.id]);
					}

					cplex.addEq(baseConstraint, airport.finalAircraftNumber[j]);
				}			
			}
			
			// 5. 对于每一个联程航班，两趟航班必须由同一个飞机执行
			if(isAllowToCancelSingleFlightInAnConnectingFlight) {
				for (ConnectingFlightpair cf : cfList) {
					
					IloLinearNumExpr cont = cplex.linearNumExpr();
					
					for (FlightArc arc : cf.firstFlight.flightarcList) {
						cont.addTerm(1, x[arc.id]);
					}
					cont.addTerm(-1, z[cf.secondFlight.IDInCPLEXModel]);

					cplex.addLe(cont, 0);

					cont = cplex.linearNumExpr();

					for (FlightArc arc : cf.secondFlight.flightarcList) {
						cont.addTerm(1, x[arc.id]);		
					}
					cont.addTerm(-1, z[cf.firstFlight.IDInCPLEXModel]);

					cplex.addLe(cont, 0);
				}
			}
			
			//6. 乘客相关约束
			for(Itinerary ite:itineraryList) {
				IloLinearNumExpr iteNumConstraint = cplex.linearNumExpr();
				for(FlightSectionItinerary fsi:ite.flightSectionItineraryList) {
					iteNumConstraint.addTerm(1, passX[fsi.id]); 
				}
				iteNumConstraint.addTerm(1, passCancel[ite.id]);
				
				cplex.addEq(iteNumConstraint, ite.volume);
			}
			
			//7. 座位相关约束
			for(FlightSection fs:flightSectionList) {
				IloLinearNumExpr seatConstraint = cplex.linearNumExpr();
				for(FlightSectionItinerary fsi:fs.flightSectionItineraryList) {
					seatConstraint.addTerm(1, passX[fsi.id]);
				}
				
				for(FlightArc arc:fs.flightArcList) {
					if(arc.isIncludedInConnecting) {
						seatConstraint.addTerm(-arc.passengerCapacity, beta[arc.connectingArc.id]);
					}else {
						seatConstraint.addTerm(-arc.passengerCapacity, x[arc .id]);						
					}
				}
				
				cplex.addLe(seatConstraint, 0);
			}

			//8. 机场起降约束
			for(String key:sce.keyList) {
				IloLinearNumExpr airportConstraint = cplex.linearNumExpr();
				List<FlightArc> faList = sce.airportFlightArcMap.get(key);
				List<ConnectingArc> caList = sce.airportConnectingArcMap.get(key);
				
				for(FlightArc arc:faList) {
					airportConstraint.addTerm(1, x[arc.id]);
				}
				for(ConnectingArc arc:caList) {
					airportConstraint.addTerm(1, beta[arc.id]);
				}
				
				cplex.addLe(airportConstraint, sce.affectAirportLdnTkfCapacityMap.get(key));
			}
			
			//9. 停机约束
			for(Integer airport:sce.affectedAirportSet) {
				IloLinearNumExpr parkingConstraint = cplex.linearNumExpr();
				List<GroundArc> gaList = sce.affectedAirportCoverParkLimitGroundArcMap.get(airport);
				for(GroundArc ga:gaList) {
					parkingConstraint.addTerm(1, y[ga.id]);					
				}
				
				cplex.addLe(parkingConstraint, sce.affectedAirportParkingLimitMap.get(airport));
			}
			
			//10. 25 和 67 停机约束
			IloLinearNumExpr parkingConstraint25 = cplex.linearNumExpr();
			for(GroundArc ga:sce.airport25ClosureGroundArcList){
				parkingConstraint25.addTerm(1, y[ga.id]);
			}
			for(FlightArc arc:sce.airport25ClosureFlightArcList){
				parkingConstraint25.addTerm(1, x[arc.id]);
			}
			for(ConnectingArc arc:sce.airport25ClosureConnectingArcList){
				parkingConstraint25.addTerm(1, beta[arc.id]);
			}
			cplex.addLe(parkingConstraint25, sce.airport25ParkingLimit);
			
			IloLinearNumExpr parkingConstraint67 = cplex.linearNumExpr();
			for(GroundArc ga:sce.airport67ClosureGroundArcList){
				parkingConstraint67.addTerm(1, y[ga.id]);
			}
			for(FlightArc arc:sce.airport67ClosureFlightArcList){
				parkingConstraint67.addTerm(1, x[arc.id]);
			}
			for(ConnectingArc arc:sce.airport67ClosureConnectingArcList){
				parkingConstraint67.addTerm(1, beta[arc.id]);
			}
			cplex.addLe(parkingConstraint67, sce.airport67ParkingLimit);
			
			if(cplex.solve()){
		
				if(isFractional){
					System.out.println("fractional value:"+cplex.getObjValue()+"  ");
				
					Parameter.fractionalObjective = cplex.getObjValue();
					
					try {
						File file = new File("linearsolution.csv");
						if(file.exists()){
							file.delete();
						}
						
						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							fa.fractionalFlow = cplex.getValue(x[fa.id]);
						}
					}
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							arc.fractionalFlow = cplex.getValue(beta[arc.id]);
						}
					}
					for(GroundArc ga:groundArcList){
						if(cplex.getValue(y[ga.id]) > 1e-6){
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}
					
					StringBuilder sb = new StringBuilder();
					for(Aircraft a:aircraftList){
						System.out.println("aircraft:"+a.id);
						boolean isContinue = true;
						
						while(isContinue){
							Node currentNode = a.sourceNode;
							
							double flowOut = 0;
							for(FlightArc arc:currentNode.flowoutFlightArcList){
								flowOut += arc.fractionalFlow;
							}
							for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
								flowOut += arc.fractionalFlow;
							}
							for(GroundArc arc:currentNode.flowoutGroundArcList){
								flowOut += arc.fractionalFlow;
							}
							
							System.out.println("flow out:"+flowOut);
							if(flowOut > 1e-6){
								
								Path p = new Path();
								p.aircraft = a;
								
								while(!currentNode.isSink){
									
									double maximumFlow = -1e-6;
									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for(FlightArc arc:currentNode.flowoutFlightArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}
									
									for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}
									
									for(GroundArc arc:currentNode.flowoutGroundArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}
									
									if(maxFlightArc != null){
										currentNode = maxFlightArc.toNode; 
										p.flightArcList.add(maxFlightArc);
									}
									if(maxConnectingArc != null){
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if(maxGroundArc != null){
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}
									
								}	
								

								p.value = 1.0;
								for(FlightArc arc:p.flightArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(ConnectingArc arc:p.connectingArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(GroundArc arc:p.groundArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								
								for(FlightArc arc:p.flightArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(ConnectingArc arc:p.connectingArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(GroundArc arc:p.groundArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								
								System.out.println("p value:"+p.value);
								
								sb.append(p.toString()+"\n");
							}else{
								isContinue = false;
							}
							
						}					
					}
					
					try {
						MyFile.writeTxtFile(sb.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}else{
					//if we solve it integrally 
					System.out.println("solve: obj = "+cplex.getObjValue());
					
					solution.objValue = cplex.getObjValue();
					Parameter.objective += cplex.getObjValue();
					
					
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							solution.selectedFlightArcList.add(fa);
							
							//更新flight arc的时间
							fa.flight.actualTakeoffT = fa.takeoffTime;
							fa.flight.actualLandingT = fa.landingTime;
						}
					}
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							
							solution.selectedConnectingArcList.add(arc);
							//更新flight arc的时间

							arc.connectingFlightPair.firstFlight.actualTakeoffT = arc.firstArc.takeoffTime;
							arc.connectingFlightPair.firstFlight.actualLandingT = arc.firstArc.landingTime;
							
							arc.connectingFlightPair.secondFlight.actualTakeoffT = arc.secondArc.takeoffTime;
							arc.connectingFlightPair.secondFlight.actualLandingT = arc.secondArc.landingTime;
						}
					}
					
					for(int i=0;i<flightSectionItineraryList.size();i++) {
						FlightSectionItinerary fsi = flightSectionItineraryList.get(i);
						if(cplex.getValue(passX[i]) > 1e-6){
							//更新具体转签行程信息
							fsi.volume = cplex.getValue(passX[i]);
						}
					}
					
					Flight f947 = sce.flightList.get(946);
					for(FlightSection fs:f947.flightSectionList){
						double capacity = 0;
						for(FlightArc arc:fs.flightArcList){
							if(arc.isIncludedInConnecting) {
								capacity += arc.passengerCapacity * cplex.getValue(beta[arc.connectingArc.id]);
								if(cplex.getValue(beta[arc.connectingArc.id]) > 1e-6){
									System.out.println("this way 1:"+(cplex.getValue(beta[arc.connectingArc.id])));
								}
							}else {
								capacity += arc.passengerCapacity * cplex.getValue(x[arc .id]);
								if(cplex.getValue(x[arc .id]) > 1e-6){
									System.out.println("this way 2:"+(cplex.getValue(x[arc .id]))+"  arc id："+arc.id);
								}
							}
						}
						
						for(FlightSectionItinerary fsi: fs.flightSectionItineraryList){
							
							if(fsi.volume > 1e-6){
								System.out.println("itinerary "+fsi.itinerary.flight.id+" assigned to flight "+fs.flight.id+"  volume:"+fsi.volume+"  capacity:"+capacity+"  unit cost:"+fsi.unitCost);								
							}
						}
					}	
					
					//检查联程航班约束
					if(isAllowToCancelSingleFlightInAnConnectingFlight) {
						for (ConnectingFlightpair cf : cfList) {
							if(cf.firstFlight.id == 947 && cf.secondFlight.id == 961){
								System.out.println("we find this connecting flight");
								
								for (FlightArc arc : cf.firstFlight.flightarcList) {
									if(arc.id == 86569){
										System.out.println("this arc is selected:"+cplex.getValue(x[arc.id]));
									}
								}
								System.out.println("corresponding flight:"+cf.secondFlight.id+" "+cf.secondFlight.IDInCPLEXModel+" "+cplex.getValue(z[cf.secondFlight.IDInCPLEXModel]));
							}
							
						}
					}

					int cancelN1 = 0;
					int cancelN2 = 0;
					for(int i=0;i<flightList.size();i++){
						Flight f = flightList.get(i);
						if(f.isCancelled){
							cancelN1++;
						}
					}
					
					
					for(int i=0;i<flightList.size();i++){
						Flight f = flightList.get(i);
						if(cplex.getValue(z[i]) > 1e-6){
							solution.cancelledFlightList.add(f);
							
							cancelN2++;
						}
					}
					
					for(String key:sce.keyList) {
						if(key.startsWith("49_")){
							List<FlightArc> faList = sce.airportFlightArcMap.get(key);
							List<ConnectingArc> caList = sce.airportConnectingArcMap.get(key);
							
							for(FlightArc arc:faList) {
								if(cplex.getValue(x[arc.id]) > 1e-6){
									System.out.println("key:"+key+" "+arc.flight.id+" "+Parameter.airportBeforeTyphoonTimeWindowStart+"->"+Parameter.airportBeforeTyphoonTimeWindowEnd);
								}
							}
							for(ConnectingArc arc:caList) {
								if(cplex.getValue(beta[arc.id]) > 1e-6){
									System.out.println("key:"+key+" "+arc.firstArc.flight.id+" "+arc.secondArc.flight.id+" "+Parameter.airportBeforeTyphoonTimeWindowStart+"->"+Parameter.airportBeforeTyphoonTimeWindowEnd);
								}
							}
						}
					}
					
					System.out.println("saved flights:"+cancelN1+" "+cancelN2);
					
					try {
						File file = new File("linearsolution.csv");
						if(file.exists()){
							file.delete();
						}
						
						MyFile.creatTxtFile("linearsolution.csv");
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					double totalArcCost = 0;
					for(FlightArc fa:flightArcList){
						
						if(cplex.getValue(x[fa.id])>1e-6){
							fa.fractionalFlow = cplex.getValue(x[fa.id]);
							
							System.out.println("fa:"+fa.fractionalFlow+"  "+fa.cost+" "+fa.delay+" "+fa.aircraft.id+" "+fa.flight.initialAircraft.id+"  "+fa.aircraft.type+" "+fa.flight.initialAircraftType+" "+fa.flight.id+" "+fa.flight.isIncludedInConnecting);
							totalArcCost += fa.cost;
						}
					}
					System.out.println("totalArcCost:"+totalArcCost);
					
					
					for(ConnectingArc arc:connectingArcList){
						if(cplex.getValue(beta[arc.id]) > 1e-6){
							arc.fractionalFlow = cplex.getValue(beta[arc.id]);
						}
					}
					for(GroundArc ga:groundArcList){
						if(cplex.getValue(y[ga.id]) > 1e-6){
							ga.fractionalFlow = cplex.getValue(y[ga.id]);
						}
					}
					

                    int[] aaa = {91,99,12,4,5,130,32,45,62,73};

                    for(int aId:aaa){
                    	Aircraft a = aircraftList.get(aId-1);
                    	for(GroundArc ga:a.groundArcList){
                    		if(ga.fractionalFlow > 1e-6){
                    			if(ga.fromNode.time <= 10320 && ga.toNode.time >= 10321){
                    				System.out.println("this  a:"+a.id+" "+ga.fractionalFlow);
                    			}
                    		}
                    	}
                    }
					
					
					StringBuilder sb = new StringBuilder();
					for(Aircraft a:aircraftList){
						System.out.println("aircraft:"+a.id);
						boolean isContinue = true;
						
						while(isContinue){
							Node currentNode = a.sourceNode;
							
							double flowOut = 0;
							for(FlightArc arc:currentNode.flowoutFlightArcList){
								flowOut += arc.fractionalFlow;
							}
							for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
								flowOut += arc.fractionalFlow;
							}
							for(GroundArc arc:currentNode.flowoutGroundArcList){
								flowOut += arc.fractionalFlow;
							}
							
							System.out.println("flow out:"+flowOut);
							if(flowOut > 1e-6){
								
								Path p = new Path();
								p.aircraft = a;
								
								while(!currentNode.isSink){
									
									double maximumFlow = -1e-6;
									FlightArc maxFlightArc = null;
									ConnectingArc maxConnectingArc = null;
									GroundArc maxGroundArc = null;
									for(FlightArc arc:currentNode.flowoutFlightArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = arc;
											maxConnectingArc = null;
											maxGroundArc = null;
										}
									}
									
									for(ConnectingArc arc:currentNode.flowoutConnectingArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = arc;
											maxGroundArc = null;
										}
									}
									
									for(GroundArc arc:currentNode.flowoutGroundArcList){
										if(arc.fractionalFlow > maximumFlow){
											maximumFlow = arc.fractionalFlow;
											maxFlightArc = null;
											maxConnectingArc = null;
											maxGroundArc = arc;
										}
									}
									
									if(maxFlightArc != null){
										currentNode = maxFlightArc.toNode; 
										p.flightArcList.add(maxFlightArc);
									}
									if(maxConnectingArc != null){
										currentNode = maxConnectingArc.toNode;
										p.connectingArcList.add(maxConnectingArc);
									}
									if(maxGroundArc != null){
										currentNode = maxGroundArc.toNode;
										p.groundArcList.add(maxGroundArc);
									}
									
								}	
								

								p.value = 1.0;
								for(FlightArc arc:p.flightArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(ConnectingArc arc:p.connectingArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								for(GroundArc arc:p.groundArcList){
									p.value = Math.min(p.value, arc.fractionalFlow);
								}
								
								for(FlightArc arc:p.flightArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(ConnectingArc arc:p.connectingArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								for(GroundArc arc:p.groundArcList){
									arc.fractionalFlow = arc.fractionalFlow - p.value;
								}
								
								System.out.println("p value:"+p.value);
								
								sb.append(p.toString()+"\n");
							}else{
								isContinue = false;
							}
							
						}					
					}
					
					try {
						MyFile.writeTxtFile(sb.toString());
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
											
					int numOfMissedConnections = 0;
					for(TransferPassenger pt:sce.transferPassengerList){
						if(pt.inFlight.actualLandingT + pt.minTurnaroundTime > pt.outFlight.actualTakeoffT){
							numOfMissedConnections += pt.volume;
						}
					}
					System.out.println("numOfMissedConnections:"+numOfMissedConnections);
				}
			
			}else{
				System.out.println("Infeasible!!!");
			}
			
			cplex.end();
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return solution;
	}
	
}
