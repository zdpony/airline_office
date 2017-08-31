package sghku.tianchi.IntelligentAviation.entity;

public class FlightItinerary {
	public int id;
	public Flight flight; //签转出去的flight
	public Itinerary thirdStageite; //来自于(ThirdStage)itinerary(可能是normal也可能是secondTrsfr)
	public double unitCost;  //decide by evaluating flight and itinerary
	public double volume;  //从ite分给flight的乘客数，to be decided by CPLEX solving model
}
