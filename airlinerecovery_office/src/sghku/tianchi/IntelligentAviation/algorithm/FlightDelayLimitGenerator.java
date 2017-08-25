package sghku.tianchi.IntelligentAviation.algorithm;

import java.util.HashSet;
import java.util.Set;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Airport;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArc;
import sghku.tianchi.IntelligentAviation.entity.Scenario;

public class FlightDelayLimitGenerator {
	//设定flight delay限制
	public void setFlightDelayLimit(Scenario scenario){
Set<Airport> typhoonAffectedAirportSet = new HashSet<>();
		
		typhoonAffectedAirportSet.add(scenario.airportList.get(48));
		typhoonAffectedAirportSet.add(scenario.airportList.get(49));
		typhoonAffectedAirportSet.add(scenario.airportList.get(60));

		for(Flight f:scenario.flightList) {
			if(f.initialTakeoffT > 1440*7 && f.initialTakeoffT <= 1440*8) {
				if(f.leg.originAirport.id == 49 || f.leg.originAirport.id == 50 || f.leg.originAirport.id == 61) {
					typhoonAffectedAirportSet.add(f.leg.destinationAirport);
				}
			}
		}
		
		//System.out.println("typhoonAffectedAirportSet:"+typhoonAffectedAirportSet.size()+" "+airportList.size());

		for(Flight f:scenario.flightList) {
			if(f.isIncludedInTimeWindow) {
				f.earlyLimit = 0;
				f.delayLimit = 60;
				
				//1.首先判断delay
				if(f.initialTakeoffT > 1440*7 && f.initialTakeoffT <= 1440*8) {
					
					int earliestT = f.initialTakeoffT;
					
					for(int t=f.initialTakeoffT;t<=f.initialTakeoffT+(f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);t+=5) {
						FlightArc fa = new FlightArc();
						fa.flight = f;
						fa.takeoffTime = t;
						fa.landingTime = t + f.flyTime;
						
						if(!fa.checkViolation()) {
							
							earliestT = t;
							break;
						}
					}
					
					if(typhoonAffectedAirportSet.contains(f.leg.originAirport) || f.leg.destinationAirport.id == 49 || f.leg.destinationAirport.id == 50 || f.leg.destinationAirport.id == 61) {											
						f.delayLimit = earliestT + 480 - f.initialTakeoffT;
					}else {
						f.delayLimit = earliestT + 60 - f.initialTakeoffT;
					}
					
				}else if(f.initialTakeoffT > 1440*6 && f.initialTakeoffT <= 1440*7){
					
				}
				
			}
		}
		
	}
}
