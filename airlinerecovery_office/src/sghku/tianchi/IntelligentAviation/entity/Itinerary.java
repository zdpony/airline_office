package sghku.tianchi.IntelligentAviation.entity;

import java.util.ArrayList;
import java.util.List;

//乘客行程
public class Itinerary {
	public int id;
	public TransferPassenger tp;
	public Flight flight;  //ite原本的flight
	public int volume;  //all normal passengers on flight
	
	public List<Flight> candidateFlightList = new ArrayList<>();  //可能可以承接这些itinerary的航班
	
	public List<FlightSectionItinerary> flightSectionItineraryList = new ArrayList<>();

	public List<FlightArc> flightArcList = new ArrayList<>();   //ite自己的flight对应的flightarc
	public List<ConnectingArc> firstConnectionArcList = new ArrayList<>(); //ite自己的flight对应的connectingarc
	public List<ConnectingArc> secondConnectingArcList = new ArrayList<>();
	
	public List<FlightArcItinerary> flightArcItineraryList = new ArrayList<>();  //从ite签转到其他flightArc的信息
	
	//public Flight preFlight;  //对second transfer，这就是第一截航班（转机乘客的前序航班），对于普通乘客，这是Null
	public List<FlightItinerary> flightIteList = new ArrayList<>();
	
	//temp
	public double cancel = 0;
	public int signChangeFlag = 0;
}
