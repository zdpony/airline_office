package sghku.tianchi.IntelligentAviation.entity;

import sghku.tianchi.IntelligentAviation.common.ExcelOperator;
import sghku.tianchi.IntelligentAviation.common.Parameter;

//联程航班所对应的联程航班arc

public class ConnectingArc {
	public int id;
	//该arc对应的联程航班
	public ConnectingFlightpair connectingFlightPair = null;
	//联程航班arc对应的第一段arc
	public FlightArc firstArc;
	//联程航班arc对应的第二段arc
	public FlightArc secondArc;
	//执行该arc的飞机
	public Aircraft aircraft = null;
	//该arc的头尾点
	public Node fromNode = null;
	public Node toNode = null;
	//该arc对应的cost
	public double cost;
	//该arc 的flow
	public int flow;
	public double fractionalFlow;

	
	//计算该联程arc的成本
	public void calculateCost(){
		/*//1. 计算换飞机型号的成本
		if(firstArc.flight.initialAircraftType != aircraft.type){
			cost += Parameter.COST_AIRCRAFTTYPE_VARIATION*firstArc.flight.importance;
		}
		if(secondArc.flight.initialAircraftType != aircraft.type){
			cost += Parameter.COST_AIRCRAFTTYPE_VARIATION*secondArc.flight.importance;
		}
		
		//2. 计算提前和延误带来的成本
		cost += Parameter.COST_EARLINESS*firstArc.flight.importance*firstArc.earliness/60.0;
		cost += Parameter.COST_EARLINESS*secondArc.flight.importance*secondArc.earliness/60.0;

		cost += Parameter.COST_DELAY*firstArc.flight.importance*firstArc.delay/60.0;
		cost += Parameter.COST_DELAY*secondArc.flight.importance*secondArc.delay/60.0;*/

		cost += ExcelOperator.getFlightTypeChangeParam(firstArc.flight.initialAircraftType, aircraft.type)*firstArc.flight.importance;
		
		if(firstArc.flight.initialAircraft.id != aircraft.id) {
			if(firstArc.flight.initialTakeoffT <= Parameter.aircraftChangeThreshold) {
				cost += Parameter.aircraftChangeCostLarge*firstArc.flight.importance;
			}else {
				cost += Parameter.aircraftChangeCostSmall*firstArc.flight.importance;
			}
		}
		
		cost += ExcelOperator.getFlightTypeChangeParam(secondArc.flight.initialAircraftType, aircraft.type)*secondArc.flight.importance;
		
		if(secondArc.flight.initialAircraft.id != aircraft.id) {
			if(secondArc.flight.initialTakeoffT <= Parameter.aircraftChangeThreshold) {
				cost += Parameter.aircraftChangeCostLarge*secondArc.flight.importance;
			}else {
				cost += Parameter.aircraftChangeCostSmall*secondArc.flight.importance;
			}
		}
		
		
		//2. 计算提前和延误带来的成本
		cost += Parameter.COST_EARLINESS*firstArc.flight.importance*firstArc.earliness/60.0;
		cost += Parameter.COST_EARLINESS*secondArc.flight.importance*secondArc.earliness/60.0;

		cost += Parameter.COST_DELAY*firstArc.flight.importance*firstArc.delay/60.0;
		cost += Parameter.COST_DELAY*secondArc.flight.importance*secondArc.delay/60.0;
				
		if(Parameter.isPassengerCostConsidered) {
			//首先考虑联程乘客的取消和延误
			int cancelConnectingPassenger = Math.max(connectingFlightPair.firstFlight.connectedPassengerNumber - aircraft.passengerCapacity, 0);
			int flyConnectingPassenger = connectingFlightPair.firstFlight.connectedPassengerNumber - cancelConnectingPassenger;
			
			cost += cancelConnectingPassenger * Parameter.passengerCancelCost;
			cost += flyConnectingPassenger * ExcelOperator.getPassengerDelayParameter(firstArc.delay);
			
			int passengerCapacity1 = aircraft.passengerCapacity - flyConnectingPassenger;
			int passengerCapacity2 = aircraft.passengerCapacity - flyConnectingPassenger;

			//考虑中转乘客延误
			cost += connectingFlightPair.firstFlight.firstTransferPassengerNumber * ExcelOperator.getPassengerDelayParameter(firstArc.delay);
			cost += connectingFlightPair.secondFlight.firstTransferPassengerNumber * ExcelOperator.getPassengerDelayParameter(secondArc.delay);
			
			//计算每一个航段剩余座位
			passengerCapacity1 = Math.max(0, passengerCapacity1-connectingFlightPair.firstFlight.transferPassengerNumber);
			passengerCapacity2 = Math.max(0, passengerCapacity2-connectingFlightPair.secondFlight.transferPassengerNumber);

			//考虑普通乘客的延误
			cost += Math.min(connectingFlightPair.firstFlight.normalPassengerNumber, passengerCapacity1) * ExcelOperator.getPassengerDelayParameter(firstArc.delay);
			cost += Math.min(connectingFlightPair.secondFlight.normalPassengerNumber, passengerCapacity2) * ExcelOperator.getPassengerDelayParameter(secondArc.delay);		
			
			//普通乘客的取消成本不在arc中计算
		}		
	}
	
	public void update() {
		this.firstArc.flight.aircraft = this.aircraft;
		this.firstArc.flight.actualTakeoffT = this.firstArc.takeoffTime;
		this.firstArc.flight.actualLandingT = this.firstArc.landingTime;
		
		this.firstArc.flight.isCancelled = false;
		
		this.secondArc.flight.aircraft = this.aircraft;
		this.secondArc.flight.actualTakeoffT = this.secondArc.takeoffTime;
		this.secondArc.flight.actualLandingT = this.secondArc.landingTime;
		
		this.secondArc.flight.isCancelled = false;
		
		this.aircraft.flightList.add(this.firstArc.flight);
		this.aircraft.flightList.add(this.secondArc.flight);
	}
}
