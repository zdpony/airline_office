package sghku.tianchi.IntelligentAviation;

import org.apache.poi.sl.draw.geom.MaxExpression;

import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.entity.Flight;
import sghku.tianchi.IntelligentAviation.entity.FlightArcItinerary;
import sghku.tianchi.IntelligentAviation.entity.Itinerary;
import sghku.tianchi.IntelligentAviation.entity.Scenario;
import sghku.tianchi.IntelligentAviation.entity.TransferPassenger;

public class ThirdStageRecovery {
	public static void main(String[] args) {

		Parameter.isPassengerCostConsidered = true;
		Parameter.isReadFixedRoutes = true;
		Parameter.onlySignChangeDisruptedPassenger = true;  //只能转签受影响乘客

		runThirdStage(false);

	}
	public static void runThirdStage(boolean isFractional){
		Scenario sce = new Scenario(Parameter.EXCEL_FILENAME);
		//计算每个flight剩余的座位数
		for(Flight f:sce.flightList) {
			if(f.isCancelled) {
				f.remainingSeatNum = 0;
			}else {
				int totalPassenger = 0;
				totalPassenger += f.connectedPassengerNumber;  //优先承载联程旅客（不能签转出去）
				totalPassenger += f.firstTransferPassengerNumber; //优先承载第一截转乘旅客（不能签转出去）
				//如果飞机容量容不下以上两类“重要”乘客，则得出剩余座位数为0
				if(f.aircraft.passengerCapacity<=totalPassenger) {
					System.out.println("error! connecting and firstTransfer passengers on the flight already exceeds its capacity: flight id "+f.id);
					System.exit(1);
					/*f.remainingSeatNum = 0;
					continue;*/
				}
				totalPassenger += f.normalPassengerNumber; //普通乘客总数

				//减去cancel的普通乘客数
				totalPassenger -= f.normalPassengerCancelNum;

				//减去签转出去的普通旅客
				for(FlightArcItinerary fai:f.itinerary.flightArcItineraryList) {
					totalPassenger -= fai.volume;
					if(fai.volume>0) {//有普通乘客签转出去，于是标记不能再接受其他航班签转乘客进来(注意！自己航班上的第二截转乘乘客是可以的，所以remainingSeatNum不能设为0)
						f.cannotAcceptSignChangePssgr = true;  
					}
				}

				//加上签转进来的普通旅客
				for(FlightArcItinerary fai:f.flightArcItineraryList) {
					totalPassenger += fai.volume;
				}
				if(f.aircraft.passengerCapacity - totalPassenger<0) {
					System.out.println("error! total passenger on the flight exceeds its capacity: flight id "+f.id);
					System.exit(1);
				}else {
					f.remainingSeatNum = f.aircraft.passengerCapacity - totalPassenger;
				}
			}
		}

		//计算每个flight上等待签转的转乘乘客（第二截），因为如果第一截flight cancel，则不能转签，只能cancel
		for(TransferPassenger tp:sce.transferPassengerList) {
			if(tp.inFlight.isCancelled) {
				if(!tp.outFlight.isCancelled) {  //如果第一截cancel，第二截flight没被cancel，第二截的remainingSeatNum会增加
					tp.outFlight.remainingSeatNum += tp.volume;
				}		
			}
			//如果第一截flight没被cancel，则所有第一截转乘乘客都已经坐上飞机了(因为他们有优先上飞机权！)，判断第二截flight
			else if(tp.outFlight.isCancelled) {   //如果第二截flight cancel了
				tp.outFlight.disruptedSecondTransferPssgrNum += tp.volume;		//如果cancel，把对应的volume加到flight信息里		
			}
			//如果两截都没cancel，判断是否miss-connection
			else if(tp.outFlight.actualTakeoffT-tp.inFlight.actualLandingT<tp.minTurnaroundTime){  //如果miss-connection
				tp.outFlight.remainingSeatNum += tp.volume;  //第二截的remainingSeatNum会增加
				tp.outFlight.disruptedSecondTransferPssgrNum += tp.volume;  //把对应的volume加到flight信息里		
			}
		}
		
		//transferItinerary 是被取消的（待签转的）第二截转乘旅客的信息， FlightTransferItinerary 记录他们的签转信息（从哪个flight来，到哪个flight去）

	}
}
