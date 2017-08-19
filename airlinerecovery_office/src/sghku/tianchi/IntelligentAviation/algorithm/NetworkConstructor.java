package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.NodeComparator;
import sghku.tianchi.IntelligentAviation.entity.Aircraft;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.ConnectingArc;
import sghku.tianchi.IntelligentAviation.entity.Failure;
import sghku.tianchi.IntelligentAviation.entity.FailureType;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.FlightSection;
import sghku.tianchi.IntelligentAviation.entity.GroundArc;
import sghku.tianchi.IntelligentAviation.entity.Leg;
import sghku.tianchi.IntelligentAviation.entity.ConnectingFlightpair;
import sghku.tianchi.IntelligentAviation.entity.Node;
import sghku.tianchi.IntelligentAviation.entity.ParkingInfo;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

/**
 * @author ych
 * Construct a feasible route (a sequence of flights) for all aircraft
 *
 */
public class NetworkConstructor {
	
	//第一种生成方法，根据原始schedule大范围生成arc
	public void generateArcForFlight(Aircraft aircraft, Flight f, int givenGap, Scenario scenario){
		int presetGap = 5;
		
		FlightArc arc = null;
		
		if(!f.isIncludedInTimeWindow) {
			//如果该航班在调整时间窗口外
			if(f.initialAircraft.id == aircraft.id) {			
				arc = new FlightArc();
				arc.flight = f;
				arc.aircraft = aircraft;
				arc.delay = f.fixedTakeoffTime - f.initialTakeoffT;
				
				/*arc.takeoffTime = f.initialTakeoffT+arc.delay;
				arc.landingTime = arc.takeoffTime+flyTime;*/
				
				arc.takeoffTime = f.fixedTakeoffTime;
				arc.landingTime = f.fixedLandingTime;
				
				arc.readyTime = arc.landingTime + Parameter.MIN_BUFFER_TIME;
				
				f.flightarcList.add(arc);
				aircraft.flightArcList.add(arc);				
				
				arc.calculateCost();
			}			
		}else {
			//2.1 check whether f can be brought forward and generate earliness arcs
			
			int startIndex = 0;
			int endIndex = 0;
			
			if(f.isDeadhead) {
				//如果是调机航班，则只能小范围延误
				endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
			}else {
				if(f.isAffected){
					if(f.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
				}
			}
					
			if(f.isAllowtoBringForward){
				startIndex = Parameter.MAX_LEAD_TIME/presetGap;
			}
			
			int flyTime = f.flyTime;
			
			if(f.isDeadhead) {
				flyTime = f.leg.flytimeArray[aircraft.type-1];
			}else if(f.isStraightened) {
				flyTime = f.leg.flytimeArray[aircraft.type-1];
				if(flyTime <= 0) {
					flyTime = f.connectingFlightpair.firstFlight.initialLandingT-f.connectingFlightpair.firstFlight.initialTakeoffT + f.connectingFlightpair.secondFlight.initialLandingT-f.connectingFlightpair.secondFlight.initialTakeoffT;
				}
			}
			
			for(int i=-startIndex;i<=endIndex;i++){
				
				boolean isWithinSmalGapRegionOrigin = false;
				boolean isWithinSmalGapRegionDestination = false;
				
				if (!f.isSmallGapRequired) {
					if((i*presetGap)%givenGap != 0) {
						continue;
					}
				}else {
					if((i*presetGap)%givenGap != 0) {
						if(scenario.affectedAirportSet.contains(f.leg.originAirport.id)) {
							int t = f.initialTakeoffT + i*presetGap;
							if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
								isWithinSmalGapRegionOrigin = true;
							}else {
								continue;
							}
						}else if(scenario.affectedAirportSet.contains(f.leg.destinationAirport.id)) {
							int t = f.initialLandingT + i*presetGap;
							if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
								isWithinSmalGapRegionDestination = true;
							}else {
								continue;
							}
						}						
					}
				}
								
				arc = new FlightArc();
				arc.flight = f;
				arc.aircraft = aircraft;
				if(i < 0) {
					arc.earliness = -i*presetGap;
				}else {
					arc.delay = i*presetGap;
				}
				
				arc.takeoffTime = f.initialTakeoffT+i*presetGap;
				arc.landingTime = arc.takeoffTime+flyTime;
				
				arc.readyTime = arc.landingTime + Parameter.MIN_BUFFER_TIME;
										
				
				if(!arc.checkViolation()){
					
					//如果是调剂航班，不需要做任何处理
					if(f.isDeadhead) {
						
					}else if(f.isStraightened) {
						//如果是联程拉直，将该arc加到对应的两段航班中
						f.connectingFlightpair.firstFlight.flightarcList.add(arc);
						f.connectingFlightpair.secondFlight.flightarcList.add(arc);
						
						//联程拉直航班则没有对应的flight section						
						//联程拉直乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;
						//要减去对应的联程乘客
						arc.passengerCapacity = arc.passengerCapacity - f.connectedPassengerNumber;
						
						//其他乘客全部被取消，所以不需要考虑
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);
					}else {
						f.flightarcList.add(arc);
						
						//将该arc加入到对应的flight section中
						boolean isFound = false;
						for(FlightSection currentFlightSection:f.flightSectionList) {
							if(arc.takeoffTime >= currentFlightSection.startTime && arc.takeoffTime < currentFlightSection.endTime) {
								isFound = true;
								currentFlightSection.flightArcList.add(arc);
								break;
							}
						}
						
						if(!isFound) {
							if(f.flightSectionList.get(f.flightSectionList.size()-1).endTime != arc.takeoffTime) {
								System.out.println("no flight section found 3!"+f.flightSectionList.get(f.flightSectionList.size()-1).endTime+" "+arc.takeoffTime);
							}
							f.flightSectionList.get(f.flightSectionList.size()-1).flightArcList.add(arc);
						}
						
						//乘客容量
						arc.passengerCapacity = aircraft.passengerCapacity;			
						//减去转乘乘客
						arc.passengerCapacity = arc.passengerCapacity - f.transferPassengerNumber;
						//剩下的则为有效座位
						arc.passengerCapacity = Math.max(0, arc.passengerCapacity);
					}
					
					arc.calculateCost();
					aircraft.flightArcList.add(arc);
					
					//加入对应的起降时间点
					if(isWithinSmalGapRegionOrigin) {
						int t = f.initialTakeoffT + i*presetGap;
						List<FlightArc> faList = scenario.airportFlightArcMap.get(f.leg.originAirport.id+"_"+t);
						faList.add(arc);
					}else if(isWithinSmalGapRegionDestination){
						int t = f.initialLandingT + i*presetGap;
						List<FlightArc> faList = scenario.airportFlightArcMap.get(f.leg.destinationAirport.id+"_"+t);
						faList.add(arc);
					}
				}
			}
		}
	}

	
	public void generateArcForFlightBasedOnReference(Aircraft aircraft, Flight f){
		FlightArc arc = null;
		
		//2.1 check whether f can be brought forward and generate earliness arcs
		
		int earliestT = f.initialTakeoffT;
		int latestT = 0;
		
		if(f.isDeadhead) {
			//如果是调机航班，则只能小范围延误
			latestT = f.initialTakeoffT + Parameter.NORMAL_DELAY_TIME;
		}else {
			if(f.isDomestic){
				latestT = f.initialTakeoffT + Parameter.MAX_DELAY_DOMESTIC_TIME;
			}else{
				latestT = f.initialTakeoffT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;
			}
			
		}
				
		if(f.isAllowtoBringForward){
			earliestT = f.initialTakeoffT - Parameter.MAX_LEAD_TIME;
		}
		
		int flyTime = f.flyTime;
		
		if(f.isDeadhead) {
			flyTime = f.leg.flytimeArray[aircraft.type-1];
		}else if(f.isStraightened) {
			flyTime = f.leg.flytimeArray[aircraft.type-1];
			if(flyTime <= 0) {
				flyTime = f.connectingFlightpair.firstFlight.initialLandingT-f.connectingFlightpair.firstFlight.initialTakeoffT + f.connectingFlightpair.secondFlight.initialLandingT-f.connectingFlightpair.secondFlight.initialTakeoffT;
			}
		}
				
		for(int i=Parameter.neighStartIndex;i<=Parameter.neighEndIndex;i++){
			
			arc = new FlightArc();
			arc.flight = f;
			arc.aircraft = aircraft;
			
			arc.takeoffTime = f.actualTakeoffT+i*Parameter.neighGap;
			
			if(arc.takeoffTime < earliestT || arc.takeoffTime > latestT) {
				continue;
			}

			int delay = arc.takeoffTime - f.initialTakeoffT;
			
			if(delay < 0) {
				arc.earliness = -delay;
			}else {
				arc.delay = delay;
			}
						
			arc.landingTime = arc.takeoffTime+flyTime;
			
			arc.readyTime = arc.landingTime + Parameter.MIN_BUFFER_TIME;
									
			if(!arc.checkViolation()){
				arc.calculateCost();
				//如果是调剂航班，不需要做任何处理
				if(f.isDeadhead) {
					
				}else if(f.isStraightened) {
					//如果是联程拉直，将该arc加到对应的两段航班中
					f.connectingFlightpair.firstFlight.flightarcList.add(arc);
					f.connectingFlightpair.secondFlight.flightarcList.add(arc);
				}else {
					f.flightarcList.add(arc);
				}
				
				aircraft.flightArcList.add(arc);
			}
		}
	}
	
	public void generateArcForConnectingFlightPair(Aircraft aircraft, ConnectingFlightpair cf, int givenGap, boolean isGenerateArcForEachFlight, Scenario scenario){
		int presetGap = 5;
		
		int connectionTime = Math.min(cf.secondFlight.initialTakeoffT-cf.firstFlight.initialLandingT, Parameter.MIN_BUFFER_TIME);
		List<FlightArc> firstFlightArcList = new ArrayList<>();
		List<ConnectingArc> connectingArcList = new ArrayList<>();
		
		//如果该联程航班在调整窗口之外
		if(!cf.firstFlight.isIncludedInTimeWindow) {
			if(cf.firstFlight.initialAircraft.id == aircraft.id) {
				
				//构建第一个flight arc
				
				FlightArc firstArc = new FlightArc();
				firstArc.flight = cf.firstFlight;
				firstArc.aircraft = aircraft;
				firstArc.delay = cf.firstFlight.fixedTakeoffTime - cf.firstFlight.initialTakeoffT;
			
				firstArc.takeoffTime = cf.firstFlight.initialTakeoffT+firstArc.delay;
				firstArc.landingTime = firstArc.takeoffTime+cf.firstFlight.flyTime;
				firstArc.readyTime = firstArc.landingTime + connectionTime;
			
				//构建第二个flight arc
				FlightArc secondArc = new FlightArc();
				secondArc.flight = cf.secondFlight;
				secondArc.aircraft = aircraft;
				secondArc.delay = cf.secondFlight.fixedTakeoffTime - cf.secondFlight.initialTakeoffT;
				secondArc.takeoffTime = cf.secondFlight.initialTakeoffT+secondArc.delay;
				secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
				secondArc.readyTime = secondArc.landingTime + Parameter.MIN_BUFFER_TIME;

				ConnectingArc ca = new ConnectingArc();
				ca.firstArc = firstArc;
				ca.secondArc = secondArc;
				ca.aircraft = aircraft;
				
				aircraft.connectingArcList.add(ca);
				
				cf.firstFlight.connectingarcList.add(ca);
				cf.secondFlight.connectingarcList.add(ca);
				ca.connectingFlightPair = cf;
				
				ca.calculateCost();
			}
		}else {
			//otherwise, create a set of connecting arcs for this connecting flight
			
			if(!aircraft.tabuLegs.contains(cf.firstFlight.leg) && !aircraft.tabuLegs.contains(cf.secondFlight.leg)){
				//only if this leg is not in the tabu list of the corresponding aircraft
				FlightArc arc = null;
		
				//2.1 check whether f can be brought forward and generate earliness arcs
				int startIndex = 0;

				if(cf.firstFlight.isAllowtoBringForward){
					startIndex = Parameter.MAX_LEAD_TIME/presetGap;				
				}

				//2.3 generate delay arcs
				int endIndex = 0;
				if(cf.isAffected){
					if(cf.firstFlight.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;								
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;		
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;		
				}
				
				for(int i=-startIndex;i<=endIndex;i++){
	
					boolean isWithinAffectedRegionOrigin1 = false;
					boolean isWithinAffectedRegionDestination1 = false;
					
					if (!cf.firstFlight.isSmallGapRequired) {
						if((i*presetGap)%givenGap != 0) {
							continue;
						}
					}else {
						if((i*presetGap)%givenGap != 0) {
							if(scenario.affectedAirportSet.contains(cf.firstFlight.leg.originAirport.id)) {
								int t = cf.firstFlight.initialTakeoffT + i*presetGap;
								if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
									isWithinAffectedRegionOrigin1 = true;
								}else {
									continue;
								}
							}else if(scenario.affectedAirportSet.contains(cf.firstFlight.leg.destinationAirport.id)) {
								int t = cf.firstFlight.initialLandingT + i*presetGap;
								if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
									isWithinAffectedRegionDestination1 = true;
								}else {
									continue;
								}
							}
							
						}
					}
										
					arc = new FlightArc();
					arc.flight = cf.firstFlight;
					arc.aircraft = aircraft;
					if(i < 0) {
						arc.earliness = -i*presetGap;	
					}else {
						arc.delay = i*presetGap;
					}
				
					arc.takeoffTime = cf.firstFlight.initialTakeoffT+i*presetGap;
					arc.landingTime = arc.takeoffTime+cf.firstFlight.flyTime;
					arc.readyTime = arc.landingTime + connectionTime;
											
					if(!arc.checkViolation()){
						firstFlightArcList.add(arc);
						
						arc.isWithinAffectedRegionOrigin = isWithinAffectedRegionOrigin1;
						arc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination1;
					}
				}
			}
			
			for(FlightArc firstArc:firstFlightArcList){
				
				if(cf.secondFlight.isAllowtoBringForward){
					int startIndex = Parameter.MAX_LEAD_TIME/presetGap;

					for(int i=startIndex;i>0;i--){
												
						boolean isWithinAffectedRegionOrigin2 = false;
						boolean isWithinAffectedRegionDestination2 = false;
							
						if (!cf.secondFlight.isSmallGapRequired) {
							if((i*presetGap)%givenGap != 0) {
								continue;
							}
						}else {
							if((i*presetGap)%givenGap != 0) {
								if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.originAirport.id)) {
									int t = cf.secondFlight.initialTakeoffT + i*presetGap;
									if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
										isWithinAffectedRegionOrigin2 = true;
									}else {
										continue;
									}
								}else if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.destinationAirport.id)) {
									int t = cf.secondFlight.initialLandingT + i*presetGap;
									if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
										isWithinAffectedRegionDestination2 = true;
									}else {
										continue;
									}
								}
								
							}
						}
						
						if(cf.secondFlight.initialTakeoffT-presetGap*i >= firstArc.readyTime){
							FlightArc secondArc = new FlightArc();
							secondArc.flight = cf.secondFlight;
							secondArc.aircraft = aircraft;
							secondArc.earliness = i*presetGap;
							secondArc.takeoffTime = cf.secondFlight.initialTakeoffT-secondArc.earliness;
							secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
							secondArc.readyTime = secondArc.landingTime + Parameter.MIN_BUFFER_TIME;

							secondArc.isWithinAffectedRegionOrigin =  isWithinAffectedRegionOrigin2;
							secondArc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination2;
							
							if(!secondArc.checkViolation()){
								ConnectingArc ca = new ConnectingArc();
								ca.firstArc = firstArc;
								ca.secondArc = secondArc;
								ca.aircraft = aircraft;
								
								aircraft.connectingArcList.add(ca);
								
								cf.firstFlight.connectingarcList.add(ca);
								cf.secondFlight.connectingarcList.add(ca);
								ca.connectingFlightPair = cf;
								
								connectingArcList.add(ca);
								ca.calculateCost();
							}
						}
						
					}
				}
								
				int endIndex = 0;
				if(cf.isAffected){
					if(cf.secondFlight.isDomestic){
						endIndex = Parameter.MAX_DELAY_DOMESTIC_TIME/presetGap;								
					}else{
						endIndex = Parameter.MAX_DELAY_INTERNATIONAL_TIME/presetGap;		
					}
				}else{
					endIndex = Parameter.NORMAL_DELAY_TIME/presetGap;
				}
				
				for(int i=0;i<=endIndex;i++){
					
					boolean isWithinAffectedRegionOrigin2 = false;
					boolean isWithinAffectedRegionDestination2 = false;
					
					if (!cf.secondFlight.isSmallGapRequired) {
						if((i*presetGap)%givenGap != 0) {
							continue;
						}
					}else {
						if((i*presetGap)%givenGap != 0) {
							if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.originAirport.id)) {
								int t = cf.secondFlight.initialTakeoffT + i*presetGap;
								if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
									isWithinAffectedRegionOrigin2 = true;
								}else {
									continue;
								}
							}else if(scenario.affectedAirportSet.contains(cf.secondFlight.leg.destinationAirport.id)) {
								int t = cf.secondFlight.initialLandingT + i*presetGap;
								if((t >= Parameter.airportFirstTimeWindowStart && t <= Parameter.airportFirstTimeWindowEnd) || (t >= Parameter.airportSecondTimeWindowStart && t <= Parameter.airportSecondTimeWindowEnd)) {
									isWithinAffectedRegionDestination2 = true;
								}else {
									continue;
								}
							}
							
						}
					}
					
					if(cf.secondFlight.initialTakeoffT+presetGap*i >= firstArc.readyTime){
						
						FlightArc secondArc = new FlightArc();
						secondArc.flight = cf.secondFlight;
						secondArc.aircraft = aircraft;
						secondArc.delay = i*presetGap;
						secondArc.takeoffTime = cf.secondFlight.initialTakeoffT+secondArc.delay;
						secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
						secondArc.readyTime = secondArc.landingTime + Parameter.MIN_BUFFER_TIME;

						secondArc.isWithinAffectedRegionOrigin =  isWithinAffectedRegionOrigin2;
						secondArc.isWithinAffectedRegionDestination = isWithinAffectedRegionDestination2;
						
						if(!secondArc.checkViolation()){
							ConnectingArc ca = new ConnectingArc();
							ca.firstArc = firstArc;
							ca.secondArc = secondArc;
							
							aircraft.connectingArcList.add(ca);
							
							cf.firstFlight.connectingarcList.add(ca);
							cf.secondFlight.connectingarcList.add(ca);
							ca.connectingFlightPair = cf;
							
							ca.aircraft = aircraft;
							
							connectingArcList.add(ca);
							ca.calculateCost();
							
							if(!isWithinAffectedRegionOrigin2 && isWithinAffectedRegionDestination2 ) {
								break;								
							}
						}
					}			
				}
				
			}
			
			for(ConnectingArc arc:connectingArcList) {
				//加入到对应的机场
				if(arc.firstArc.isWithinAffectedRegionOrigin) {
					List<ConnectingArc> caList = scenario.airportConnectingArcMap.get(arc.firstArc.flight.leg.originAirport.id+"_"+arc.firstArc.takeoffTime);
					caList.add(arc);
				}
				if(arc.firstArc.isWithinAffectedRegionDestination) {
					List<ConnectingArc> caList = scenario.airportConnectingArcMap.get(arc.firstArc.flight.leg.destinationAirport.id+"_"+arc.firstArc.landingTime);
					caList.add(arc);
				}
				if(arc.secondArc.isWithinAffectedRegionOrigin) {
					List<ConnectingArc> caList = scenario.airportConnectingArcMap.get(arc.secondArc.flight.leg.originAirport.id+"_"+arc.secondArc.takeoffTime);
					caList.add(arc);
				}
				if(arc.secondArc.isWithinAffectedRegionDestination) {
					List<ConnectingArc> caList = scenario.airportConnectingArcMap.get(arc.secondArc.flight.leg.destinationAirport.id+"_"+arc.secondArc.landingTime);
					caList.add(arc);
				}
				
				//设置第一个arc
				arc.firstArc.isIncludedInConnecting = true;
				arc.firstArc.connectingArc = arc;
				
				//乘客容量
				arc.firstArc.passengerCapacity = aircraft.passengerCapacity;			
				//减去联程乘客
				arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.connectedPassengerNumber;
				//减去转乘乘客
				arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.transferPassengerNumber;
				//减去普通乘客
				arc.firstArc.passengerCapacity = arc.firstArc.passengerCapacity - cf.firstFlight.normalPassengerNumber;
				//剩下的则为有效座位
				arc.firstArc.passengerCapacity = Math.max(0, arc.firstArc.passengerCapacity);
				
				boolean isFound = false;
				for(FlightSection currentFlightSection:cf.firstFlight.flightSectionList) {
					if(arc.firstArc.takeoffTime >= currentFlightSection.startTime && arc.firstArc.takeoffTime < currentFlightSection.endTime) {
						currentFlightSection.flightArcList.add(arc.firstArc);
						isFound = true;
						break;
					}
				}
				
				if(!isFound) {				
					if(arc.firstArc.takeoffTime != cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).endTime) {
						System.out.println("no flight section found! "+arc.firstArc.takeoffTime+" "+cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).endTime);
						System.exit(1);
					}
					cf.firstFlight.flightSectionList.get(cf.firstFlight.flightSectionList.size()-1).flightArcList.add(arc.firstArc);	
				}
				
				//设置第二个arc
				//乘客容量
				arc.secondArc.passengerCapacity = aircraft.passengerCapacity;			
				//减去联程乘客
				arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.firstFlight.connectedPassengerNumber;
				//减去转乘乘客
				arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.secondFlight.transferPassengerNumber;
				//减去普通乘客
				arc.secondArc.passengerCapacity = arc.secondArc.passengerCapacity - cf.secondFlight.normalPassengerNumber;
				//剩下的则为有效座位
				arc.secondArc.passengerCapacity = Math.max(0, arc.secondArc.passengerCapacity);
				
				isFound = false;
				for(FlightSection currentFlightSection:cf.secondFlight.flightSectionList) {
					if(arc.secondArc.takeoffTime >= currentFlightSection.startTime && arc.secondArc.takeoffTime < currentFlightSection.endTime) {
						currentFlightSection.flightArcList.add(arc.secondArc);
						isFound = true;
						break;
					}
				}
				
				if(!isFound) {				
					if(arc.secondArc.takeoffTime != cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).endTime) {
						System.out.println("no flight section found 2! "+arc.secondArc.takeoffTime+" "+cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).endTime);
						System.exit(1);
					}
					cf.secondFlight.flightSectionList.get(cf.secondFlight.flightSectionList.size()-1).flightArcList.add(arc.secondArc);	
				}
			}
			
			//3. 为每一个flight生成arc，可以单独取消联程航班中的一段
			if(isGenerateArcForEachFlight) {
				if(!aircraft.tabuLegs.contains(cf.firstFlight.leg)){
					generateArcForFlight(aircraft, cf.firstFlight, givenGap, scenario);
				}
				
				if(!aircraft.tabuLegs.contains(cf.secondFlight.leg)){
					generateArcForFlight(aircraft, cf.secondFlight, givenGap, scenario);
				}
			}
		}
	}
	
	
	public void generateArcForConnectingFlightPairBasedOnReference(Aircraft aircraft, ConnectingFlightpair cf, boolean isGenerateArcForEachFlight){
		//otherwise, create a set of connecting arcs for this connecting flight
		List<FlightArc> firstFlightArcList = new ArrayList<>();
		
		int connectionTime = Math.min(cf.secondFlight.initialTakeoffT-cf.firstFlight.initialLandingT, Parameter.MIN_BUFFER_TIME);
		
		if(!aircraft.tabuLegs.contains(cf.firstFlight.leg) && !aircraft.tabuLegs.contains(cf.secondFlight.leg)){
			//only if this leg is not in the tabu list of the corresponding aircraft
			FlightArc arc = null;
	
			//2.1 check whether f can be brought forward and generate earliness arcs
			int earliestT = 0;

			if(cf.firstFlight.isAllowtoBringForward){
				earliestT = cf.firstFlight.initialTakeoffT - Parameter.MAX_LEAD_TIME;				
			}

			//2.3 generate delay arcs
			int latestT = 0;
			
			if(cf.firstFlight.isDomestic){
				latestT = cf.firstFlight.initialTakeoffT + Parameter.MAX_DELAY_DOMESTIC_TIME;								
			}else{
				latestT = cf.firstFlight.initialTakeoffT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;		
			}
			
			for(int i=Parameter.neighStartIndex;i<=Parameter.neighEndIndex;i++){
				
				
				arc = new FlightArc();
				arc.flight = cf.firstFlight;
				arc.aircraft = aircraft;
				
				arc.takeoffTime = cf.firstFlight.actualTakeoffT+i*Parameter.neighGap;
				
				if(arc.takeoffTime < earliestT || arc.takeoffTime > latestT) {
					continue;
				}
				
				int delay = arc.takeoffTime - cf.firstFlight.initialTakeoffT;
				if(delay < 0) {
					arc.earliness = -delay;	
				}else {
					arc.delay = delay;
				}
				
				arc.landingTime = arc.takeoffTime+cf.firstFlight.flyTime;
				arc.readyTime = arc.landingTime + connectionTime;
										
				if(!arc.checkViolation()){
					
					firstFlightArcList.add(arc);
				}
			}
		}
		
		for(FlightArc firstArc:firstFlightArcList){
			
			int earliestT = cf.secondFlight.initialTakeoffT;
			
			if(cf.secondFlight.isAllowtoBringForward){
				earliestT = cf.secondFlight.initialTakeoffT - Parameter.MAX_LEAD_TIME;
			}
							
			int latestT = 0;
			if(cf.secondFlight.isDomestic){
				latestT = cf.secondFlight.initialTakeoffT + Parameter.MAX_DELAY_DOMESTIC_TIME;								
			}else{
				latestT = cf.secondFlight.initialTakeoffT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;		
			}
			
			for(int i=Parameter.neighStartIndex;i<=Parameter.neighEndIndex;i++) {
				int takeoffT = cf.secondFlight.actualTakeoffT + i*Parameter.neighGap;
				
				if(takeoffT < earliestT || takeoffT > latestT) {
					continue;
				}
				
				if(takeoffT >= firstArc.readyTime){
					
					
					FlightArc secondArc = new FlightArc();
					secondArc.flight = cf.secondFlight;
					secondArc.aircraft = aircraft;
					
					int delay = takeoffT - cf.secondFlight.initialTakeoffT;
					if(delay < 0) {
						secondArc.earliness = -delay;
					}else {
						secondArc.delay = delay;	
					}
					
					secondArc.takeoffTime = takeoffT;
					secondArc.landingTime = secondArc.takeoffTime+cf.secondFlight.flyTime;
					secondArc.readyTime = secondArc.landingTime + Parameter.MIN_BUFFER_TIME;

					if(!secondArc.checkViolation()){
						ConnectingArc ca = new ConnectingArc();
						ca.firstArc = firstArc;
						ca.secondArc = secondArc;
						
						aircraft.connectingArcList.add(ca);
						
						cf.firstFlight.connectingarcList.add(ca);
						cf.secondFlight.connectingarcList.add(ca);
						ca.connectingFlightPair = cf;
						
						ca.aircraft = aircraft;
						ca.calculateCost();
						
						if(i >= 0) {
							break;
						}						
					}
				}
			}
			
		}
		
		//3. 为每一个flight生成arc，可以单独取消联程航班中的一段
		if(isGenerateArcForEachFlight) {
			if(!aircraft.tabuLegs.contains(cf.firstFlight.leg)){
				generateArcForFlightBasedOnReference(aircraft, cf.firstFlight);
			}
			
			if(!aircraft.tabuLegs.contains(cf.secondFlight.leg)){
				generateArcForFlightBasedOnReference(aircraft, cf.secondFlight);
			}
		}
	}
	
	//生成点和地面arc
	public void generateNodes(List<Aircraft> aircraftList, List<Airport> airportList, Scenario scenario){
		//2. generate nodes for each arc		
		for(Aircraft aircraft:aircraftList){
			//1. clear node map for each airport
			for(int i=0;i<airportList.size();i++){
				Airport a = airportList.get(i);
				aircraft.nodeMapArray[i] = new HashMap<>();
				aircraft.nodeListArray[i] = new ArrayList<>();
			}
		
			for(FlightArc flightArc:aircraft.flightArcList){
				
				Airport departureAirport = flightArc.flight.leg.originAirport;
				Airport arrivalAirport = flightArc.flight.leg.destinationAirport;
				
				Node node = aircraft.nodeMapArray[departureAirport.id-1].get(flightArc.takeoffTime);
						
				if(node == null){
					node = new Node();
					node.airport = departureAirport;
					node.time = flightArc.takeoffTime;
					aircraft.nodeMapArray[departureAirport.id-1].put(flightArc.takeoffTime, node);
				}
				
				node.flowoutFlightArcList.add(flightArc);
				flightArc.fromNode = node;
				
				node = aircraft.nodeMapArray[arrivalAirport.id-1].get(flightArc.readyTime);
				if(node == null){
					node = new Node();
					node.airport = arrivalAirport;
					node.time = flightArc.readyTime;
					aircraft.nodeMapArray[arrivalAirport.id-1].put(flightArc.readyTime, node);
				
				}
				
				node.flowinFlightArcList.add(flightArc);
				flightArc.toNode = node;
			}
			
			for(ConnectingArc flightArc:aircraft.connectingArcList){
				
				Airport departureAirport = flightArc.firstArc.flight.leg.originAirport;
				Airport arrivalAirport = flightArc.secondArc.flight.leg.destinationAirport;
				
				int takeoffTime = flightArc.firstArc.takeoffTime;
				int readyTime = flightArc.secondArc.readyTime;
				
				Node node = aircraft.nodeMapArray[departureAirport.id-1].get(takeoffTime);
						
				if(node == null){
					node = new Node();
					node.airport = departureAirport;
					node.time = takeoffTime;
					aircraft.nodeMapArray[departureAirport.id-1].put(takeoffTime, node);
				}
				
				node.flowoutConnectingArcList.add(flightArc);
				flightArc.fromNode = node;
				
				node = aircraft.nodeMapArray[arrivalAirport.id-1].get(readyTime);
				if(node == null){
					node = new Node();
					node.airport = arrivalAirport;
					node.time = readyTime;
					aircraft.nodeMapArray[arrivalAirport.id-1].put(readyTime, node);
				
				}
				
				node.flowinConnectingArcList.add(flightArc);
				flightArc.toNode = node;
			}
			
			//生成source和sink点
			Node sourceNode = new Node();
			sourceNode.isSource = true;
			aircraft.sourceNode = sourceNode;
			
			Node sinkNode = new Node();
			sinkNode.isSink = true;
			aircraft.sinkNode = sinkNode;
					
			//3. sort nodes of each airport
			
			for(int i=0;i<airportList.size();i++){
				for(Integer key:aircraft.nodeMapArray[i].keySet()){
					aircraft.nodeListArray[i].add(aircraft.nodeMapArray[i].get(key));
				}
				
				Airport airport = airportList.get(i);
				boolean isFound = false;
				
				Collections.sort(aircraft.nodeListArray[i], new NodeComparator());
				
				if(scenario.affectedAirportSet.contains(airport.id)) {
					for(Failure scene:airport.failureList){
						if(scene.type.equals(FailureType.parking)) {
							scenario.affectedGroundArcLimitMap.put(airport.id, scene.parkingLimit);
						}
					}
				}
				
				for(int j=0;j<aircraft.nodeListArray[i].size()-1;j++){
					Node n1 = aircraft.nodeListArray[i].get(j);
					Node n2 = aircraft.nodeListArray[i].get(j+1);
					
					GroundArc groundArc = new GroundArc();
					groundArc.fromNode = n1;
					groundArc.toNode = n2;
					
					n1.flowoutGroundArcList.add(groundArc);
					n2.flowinGroundArcList.add(groundArc);
					
					aircraft.groundArcList.add(groundArc);
					
					if(scenario.affectedAirportSet.contains(airport.id)) {
						if(!isFound) {
							if(n1.time >= Parameter.airportFirstTimeWindowEnd && n2.time <= Parameter.airportSecondTimeWindowStart) {
								scenario.affectedGroundArcMap.put(airport.id, groundArc);
								isFound = true;
							}
						}						
					}
					
					/*if(!groundArc.checkViolation()){
						n1.flowoutGroundArcList.add(groundArc);
						n2.flowinGroundArcList.add(groundArc);
						
						aircraft.groundArcList.add(groundArc);
					}*/
				}
				
			}
			
			//4. construct source and sink arcs
			//如果至少有一个对应该机场的点生成
			if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0) {
				Node firstNode = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(0);
				
				GroundArc arc = new GroundArc();
				arc.fromNode = sourceNode;
				arc.toNode = firstNode;
				arc.isSource = true;
				sourceNode.flowoutGroundArcList.add(arc);
				firstNode.flowinGroundArcList.add(arc);
				aircraft.groundArcList.add(arc);
			}
			
			//对停机限制的机场飞机可以刚开停靠
			/*if(Parameter.restrictedAirportSet.contains(aircraft.initialLocation)){
				if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0){
					for(int j=1;j<aircraft.nodeListArray[aircraft.initialLocation.id-1].size();j++){
						Node n = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(j);
						
						GroundArc arc = new GroundArc();
						arc.fromNode = sourceNode;
						arc.toNode = n;
						arc.isSource = true;
						sourceNode.flowoutGroundArcList.add(arc);
						n.flowinGroundArcList.add(arc);
						aircraft.groundArcList.add(arc);
					}
				}				
			}*/
			//对停机限制的机场飞机可以刚开停靠
			if(scenario.affectedAirportSet.contains(aircraft.initialLocation.id)){
				if(aircraft.nodeListArray[aircraft.initialLocation.id-1].size() > 0){
					for(int j=1;j<aircraft.nodeListArray[aircraft.initialLocation.id-1].size();j++){
						Node n = aircraft.nodeListArray[aircraft.initialLocation.id-1].get(j);
						
						if(n.time >= Parameter.airportSecondTimeWindowStart) {
							GroundArc arc = new GroundArc();
							arc.fromNode = sourceNode;
							arc.toNode = n;
							arc.isSource = true;
							sourceNode.flowoutGroundArcList.add(arc);
							n.flowinGroundArcList.add(arc);
							aircraft.groundArcList.add(arc);
						}
					}
				}				
			}
			
			

			/*for(Airport airport:airportList){
				if(aircraft.nodeListArray[airport.id-1].size() > 0){
					if(Parameter.restrictedAirportSet.contains(airport)){
						for(Node lastNode:aircraft.nodeListArray[airport.id-1]){
							GroundArc arc = new GroundArc();
							arc.fromNode = lastNode;
							arc.toNode = sinkNode;
							arc.isSink = true;
							lastNode.flowoutGroundArcList.add(arc);
							sinkNode.flowinGroundArcList.add(arc);
							aircraft.groundArcList.add(arc);
							
							lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
						}
					}else{
						Node lastNode = aircraft.nodeListArray[airport.id-1].get(aircraft.nodeListArray[airport.id-1].size()-1);
						
						GroundArc arc = new GroundArc();
						arc.fromNode = lastNode;
						arc.toNode = sinkNode;
						arc.isSink = true;
						lastNode.flowoutGroundArcList.add(arc);
						sinkNode.flowinGroundArcList.add(arc);
						aircraft.groundArcList.add(arc);
						
						lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
					}					
				}
			}*/
			
			
			for(Airport airport:airportList){
				if(aircraft.nodeListArray[airport.id-1].size() > 0){
					if(scenario.affectedAirportSet.contains(airport.id)){
						for(int j=0;j<aircraft.nodeListArray[airport.id-1].size()-1;j++){
							
							Node lastNode = aircraft.nodeListArray[airport.id-1].get(j);
							Node nextNode = aircraft.nodeListArray[airport.id-1].get(j+1);
							
							if(lastNode.time <= Parameter.airportFirstTimeWindowEnd && nextNode.time > Parameter.airportSecondTimeWindowStart) {
								GroundArc arc = new GroundArc();
								arc.fromNode = lastNode;
								arc.toNode = sinkNode;
								arc.isSink = true;
								lastNode.flowoutGroundArcList.add(arc);
								sinkNode.flowinGroundArcList.add(arc);
								aircraft.groundArcList.add(arc);
								
								lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
								
								break;
							}							
						}
					}
					
					Node lastNode = aircraft.nodeListArray[airport.id-1].get(aircraft.nodeListArray[airport.id-1].size()-1);
					
					GroundArc arc = new GroundArc();
					arc.fromNode = lastNode;
					arc.toNode = sinkNode;
					arc.isSink = true;
					lastNode.flowoutGroundArcList.add(arc);
					sinkNode.flowinGroundArcList.add(arc);
					aircraft.groundArcList.add(arc);
					
					lastNode.airport.sinkArcList[aircraft.type-1].add(arc);
				}
			}
			
			//4.2 生成直接从source node连接到sink node的arc
			GroundArc arc = new GroundArc();
			arc.fromNode = sourceNode;
			arc.toNode = sinkNode;
			arc.isSink = true;
			sourceNode.flowoutGroundArcList.add(arc);
			sinkNode.flowinGroundArcList.add(arc);
			aircraft.groundArcList.add(arc);
			
			aircraft.initialLocation.sinkArcList[aircraft.type-1].add(arc);
			
			//5. construct turn-around arc
			arc = new GroundArc();
			arc.fromNode = sinkNode;
			arc.toNode = sourceNode;
			sinkNode.flowoutGroundArcList.add(arc);
			sourceNode.flowinGroundArcList.add(arc);
			aircraft.groundArcList.add(arc);
			aircraft.turnaroundArc = arc;
		}
		
	}
	
}
