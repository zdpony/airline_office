package sghku.tianchi.IntelligentAviation.entity;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import sghku.tianchi.IntelligentAviation.common.ExcelOperator;
import sghku.tianchi.IntelligentAviation.common.Parameter;
import sghku.tianchi.IntelligentAviation.comparator.FlightComparator;

public class Scenario {

	public List<Failure> faultList;
	public List<Airport> airportList;
	public List<Leg> legList;
	public List<Aircraft> aircraftList = new ArrayList<Aircraft>();
	public List<Flight> flightList = new ArrayList<Flight>();
	public List<PassengerTransfer> passengerTransferList;

	// 所有停机约束
	public List<ParkingInfo> parkingInfoList = new ArrayList<>();

	// 整体时间窗的最早时间和最晚时间
	public long earliestTimeLong = 0;
	public Date earliestDate = null;
	public long latestTimeLong = 0;
	public Date latestDate = null;

	// 所有联程航班
	public Map<String, ConnectingFlightpair> connectingFlightMap = new HashMap<>();
	public List<ConnectingFlightpair> connectingFlightList = null;

	// 转机乘客
	public List<TransferPassenger> transferPassengerList = new ArrayList<>();

	// 乘客信息
	public List<Itinerary> itineraryList = new ArrayList<>();

	public Scenario() {

	}

	public Scenario(String filename) {

		// 读取机场
		airportList = getAirportList(filename);
		// 读取航段
		legList = getLegList();
		// 读取所有飞机
		aircraftList = getAircraftList(filename, legList);

		// 读取所有航班
		flightList = ExcelOperator.getFlightList(filename, aircraftList, legList);
		// 读取联程航班
		connectingFlightList = ExcelOperator.getConnectingFlightList(flightList, legList);

		// 更新时间窗的最早和最晚时间
		for (Flight flight : flightList) {
			if (earliestDate == null) {
				earliestDate = flight.takeoffTime;
				earliestTimeLong = flight.takeoffTime.getTime();
			} else {
				if (flight.takeoffTime.getTime() < earliestTimeLong) {
					earliestDate = flight.takeoffTime;
					earliestTimeLong = flight.takeoffTime.getTime();
				}
			}
			if (latestDate == null) {
				latestDate = flight.landingTime;
				latestTimeLong = flight.landingTime.getTime();
			} else {
				if (flight.landingTime.getTime() > latestTimeLong) {
					latestDate = flight.landingTime;
					latestTimeLong = flight.landingTime.getTime();
				}
			}
		}

		// 将飞机飞行的航班排序，计算初始机场
		for (Aircraft a : aircraftList) {
			Collections.sort(a.flightList, new FlightComparator());
			if (a.flightList.size() > 0) {
				a.flightList.get(0).aircraft.initialLocation = a.flightList.get(0).leg.originAirport;
			}

			for (Flight f : a.flightList) {
				f.initialAircraft = a;
			}
		}

		// 读取故障信息
		faultList = ExcelOperator.getFaultList(filename, airportList, flightList, aircraftList, earliestTimeLong,
				earliestDate, latestTimeLong, latestDate);
		ExcelOperator.getClosureInfo(filename, airportList, earliestDate, latestDate);

		// 读取每一个航段的飞行信息
		ExcelOperator.readflytimeMap(filename, legList);

		// 对每一个航段检查是否可以提前，是否受到影响
		for (Flight f : flightList) {
			f.checkBringForward();
			f.checkAffected();

			// 初始化航班的飞行时间
			f.flyTime = f.initialLandingT - f.initialTakeoffT;
		}
		// 检查该联程航班是否可以被拉直以及是否受到影响
		for (ConnectingFlightpair cf : connectingFlightList) {
			cf.checkStraighten();

			cf.checkAffected();
			// 将该联程拉直航班放到map中
			connectingFlightMap.put(cf.firstFlight.id + "_" + cf.secondFlight.id, cf);

			// 设置每一个flight的inIncludedInConnecting
			cf.firstFlight.isIncludedInConnecting = true;
			cf.secondFlight.isIncludedInConnecting = true;

			cf.firstFlight.connectingFlightpair = cf;
			cf.secondFlight.connectingFlightpair = cf;

			cf.firstFlight.brotherFlight = cf.secondFlight;
			cf.secondFlight.brotherFlight = cf.firstFlight;
		}

		// 判断某一个机场是否为国内机场
		for (Flight f : flightList) {
			if (f.isDomestic) {
				f.leg.originAirport.isDomestic = true;
				f.leg.destinationAirport.isDomestic = true;
			}
		}

		// 初始化航班和飞机的信息
		for (Flight f : flightList) {
			f.actualLandingT = f.initialLandingT;
			f.actualTakeoffT = f.initialTakeoffT;
		}
		

		for (Airport a : airportList) {
			if (a.id == 49 || a.id == 50 || a.id == 61) {
				Parameter.restrictedAirportSet.add(a);
			}
		}

		// read transfer passenger
		readTransferPassengerInformation();
		
		// 判断航班是否处在调整时间窗
		for (Flight f : flightList) {
			if (f.initialTakeoffT < Parameter.timeWindowStartTime || f.initialTakeoffT > Parameter.timeWindowEndTime) {
				f.isIncludedInTimeWindow = false;
				f.fixedTakeoffTime = f.initialTakeoffT;
				f.fixedLandingTime = f.initialLandingT;
			} else {
				f.isIncludedInTimeWindow = true;
			}
		}

		// 判断前半段处于调整时间窗外的联程航班
		for (ConnectingFlightpair cf : connectingFlightList) {
			if (!cf.firstFlight.isIncludedInTimeWindow && cf.secondFlight.isIncludedInTimeWindow) {
				
				for (int i = 0; i <= 432; i++) {
					FlightArc fa = new FlightArc();
					fa.flight = cf.secondFlight;
					fa.aircraft = cf.initialAircraft;
					fa.delay = i * 5;
					fa.takeoffTime = cf.secondFlight.initialTakeoffT + fa.delay;
					fa.landingTime = cf.secondFlight.initialLandingT + fa.delay;

					if (!fa.checkViolation()) {

						cf.secondFlight.fixedTakeoffTime = fa.takeoffTime;
						cf.secondFlight.fixedLandingTime = fa.landingTime;

						break;
					}
				}

				cf.secondFlight.isIncludedInTimeWindow = false;
			}
		}

		// 生成单程乘客行程
		for (Flight f : flightList) {
			if (f.isIncludedInTimeWindow) {
				Itinerary ite = new Itinerary();
				ite.flight = f;
				ite.volume = f.normalPassengerNumber;

				itineraryList.add(ite);
			}
		}
	
		// 为每一个行程检测可以替代的航班
		for (Itinerary ite : itineraryList) {
			for (Flight f : flightList) {

				if (f.isIncludedInTimeWindow && f.leg.equals(ite.flight.leg) && f.id != ite.flight.id) {
					// 判断该航班是否可以承载该行程
					int earliestT = f.initialTakeoffT - (f.isAllowtoBringForward ? Parameter.MAX_LEAD_TIME : 0);
					// int latestT = f.initialTakeoffT +
					// (f.isDomestic?Parameter.MAX_DELAY_DOMESTIC_TIME:Parameter.MAX_DELAY_INTERNATIONAL_TIME);

					if (ite.flight.initialTakeoffT >= earliestT
							&& ite.flight.initialTakeoffT + 48 * 60 >= f.initialTakeoffT) {
						ite.candidateFlightList.add(f);
					}
				}
			}
		}

		readFlightSectionItinerary();
	}

	// 读取机场信息
	private List<Airport> getAirportList(String filename) {

		List<Airport> aList = new ArrayList<Airport>();
		for (int i = 0; i < Parameter.TOTAL_AIRPORT_NUM; i++) {
			Airport airport = new Airport();
			airport.id = i + 1;
			aList.add(airport);

		}

		return aList;
	}

	// 读取航段信息
	private List<Leg> getLegList() {

		List<Leg> lList = new ArrayList<Leg>();

		for (int i = 0; i < Parameter.TOTAL_AIRPORT_NUM; i++) {

			for (int j = 0; j < Parameter.TOTAL_AIRPORT_NUM; j++) {

				Leg leg = new Leg();
				leg.id = (i * Parameter.TOTAL_AIRPORT_NUM) + j; // ��ID�͸ú�����List�е�λ��һ��
				leg.originAirport = airportList.get(i);
				leg.destinationAirport = airportList.get(j);

				lList.add(leg);
			}

		}

		return lList;

	}

	// 读取飞机信息
	private List<Aircraft> getAircraftList(String filename, List<Leg> legList) {

		List<Aircraft> aList = new ArrayList<Aircraft>();

		for (int i = 0; i < Parameter.TOTAL_AIRCRAFT_NUM; i++) {

			Aircraft aircraft = new Aircraft();
			aircraft.id = i + 1;

			aList.add(aircraft);
		}

		if (this.faultList != null) {

			for (int i = 0; i < this.faultList.size(); i++) {

				Failure fault = this.faultList.get(i); // ��ȡ��ǰ�Ĺ�����Ϣ

				if (fault.aircraft.id != -1 && fault.aircraft.id != 0) {
					aList.get(fault.aircraft.id).faultList.add(fault); // ��ӹ�����Ϣ����Ӧ�ɻ�
				}
			}

		}

		ExcelOperator.getTabuLegs(filename, aList, legList);

		return aList;

	}

	// 读取停机约束
	public void readParkingInfor(int gap) {
		for (Airport airport : airportList) {
			for (Failure failure : airport.failureList) {
				if (failure.type.equals(FailureType.parking)) {
					int totalNum = (failure.eTime - failure.sTime) / gap;
					for (int i = 0; i < totalNum; i++) {
						int startTime = failure.eTime + i * gap;
						int endTime = failure.eTime + (i + 1) * gap;

						ParkingInfo pi = new ParkingInfo();
						pi.startTime = startTime;
						pi.endTime = endTime;
						pi.airport = airport;
						pi.parkingLimit = failure.parkingLimit;

						airport.parkingInfoList.add(pi);

						parkingInfoList.add(pi);
					}
				}
			}
		}
	}

	public double calculateObj() {
		int deadheadNum = 0; // 璋冩満鑸彮鏁?
		double cancelFlightNum = 0.0; // 鍙栨秷鑸彮鏁伴噺
		double flightTypeChangeNum = 0.0; // 鏈哄瀷鍙戠敓鍙樺寲鐨勮埅鐝暟閲?
		double connectFlightStraightenNum = 0.0; // 鑱旂▼鎷夌洿鑸彮瀵圭殑涓暟
		double totalFlightDelayHours = 0.0; // 鑸彮鎬诲欢璇椂闂达紙灏忔椂锛?
		double totalFlightAheadHours = 0.0; // 鑸彮鎬绘彁鍓嶆椂闂达紙灏忔椂锛?

		double objective = 0;

		int cn1 = 0;
		int cn2 = 0;

		for (Flight f : flightList) {
			if (f.isDeadhead) {
				deadheadNum++;
			}

			if (!f.isCancelled) {
				totalFlightDelayHours += Math.max(0.0, f.actualTakeoffT / 60.0 - f.initialTakeoffT / 60.0)
						* f.importance;
				totalFlightAheadHours += Math.max(0.0, f.initialTakeoffT / 60.0 - f.actualTakeoffT / 60.0)
						* f.importance;
				if (f.isStraightened) {
					System.out.println(f.id + f.connectingFlightpair.firstFlight.id);
					System.out.println(f.connectingFlightpair.secondFlight.id);
					connectFlightStraightenNum += f.connectingFlightpair.firstFlight.importance
							+ f.connectingFlightpair.secondFlight.importance;
				}

				if (f.aircraft.type != f.initialAircraftType) {
					flightTypeChangeNum += f.importance;
				}

			} else if (!f.isStraightened) {
				cancelFlightNum += f.importance;
			}

			if (f.isCancelled) {
				cn1++;

				if (!f.isStraightened) {
					cn2++;
				}
			}
		}
		objective += deadheadNum * Parameter.COST_DEADHEAD;
		objective += cancelFlightNum * Parameter.COST_CANCEL;
		objective += flightTypeChangeNum * Parameter.COST_AIRCRAFTTYPE_VARIATION;
		objective += connectFlightStraightenNum * Parameter.COST_STRAIGHTEN;
		objective += totalFlightDelayHours * Parameter.COST_DELAY;
		objective += totalFlightAheadHours * Parameter.COST_EARLINESS;

		System.out.println("emptyFlightNum:" + deadheadNum + " cancelFlightNum:" + cancelFlightNum
				+ " flightTypeChangeNum:" + flightTypeChangeNum + " connectFlightStraightenNum:"
				+ connectFlightStraightenNum + " totalFlightDelayHours:" + totalFlightDelayHours
				+ " totalFlightAheadHours:" + totalFlightAheadHours);

		System.out.println("cancelled flight number : " + cn1 + " " + cn2);
		return objective;
	}

	// 读取行程航班信息
	public void readFlightSectionItinerary() {
		int n = 0;
		List<Integer> delayOptionList = new ArrayList<>();
		//四个不同的延误值
		delayOptionList.add(0);
		delayOptionList.add(6 * 60);
		delayOptionList.add(24 * 60);
		delayOptionList.add(48 * 60);

		for (Itinerary ite : itineraryList) {
			for (Flight f : ite.candidateFlightList) {
				int earlistT = f.initialTakeoffT;
				int latestT = f.initialTakeoffT;

				if (f.isAllowtoBringForward) {
					earlistT = earlistT - Parameter.MAX_LEAD_TIME;
				}
				if (f.isDomestic) {
					latestT = latestT + Parameter.MAX_DELAY_DOMESTIC_TIME;
				} else {
					latestT = latestT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;
				}

				for (int d : delayOptionList) {
					int t = ite.flight.initialTakeoffT + d;

					if (t >= earlistT && t <= latestT) {
						
						f.discreteTimePointSet.add(t);
					}
				}
			}
		}
		
		for(Flight f:flightList) {
			if(f.isIncludedInTimeWindow) {
				int earlistT = f.initialTakeoffT;
				int latestT = f.initialTakeoffT;

				if (f.isAllowtoBringForward) {
					earlistT = earlistT - Parameter.MAX_LEAD_TIME;
				}
				if (f.isDomestic) {
					latestT = latestT + Parameter.MAX_DELAY_DOMESTIC_TIME;
				} else {
					latestT = latestT + Parameter.MAX_DELAY_INTERNATIONAL_TIME;
				}

				f.discreteTimePointSet.add(earlistT);
				f.discreteTimePointSet.add(latestT);
			}
		}

		for (Flight f : flightList) {
			if (f.isIncludedInTimeWindow) {
				f.discreteTimePointList.addAll(f.discreteTimePointSet);
				Collections.sort(f.discreteTimePointList);

				for (int i = 0; i < f.discreteTimePointList.size() - 1; i++) {
					int t1 = f.discreteTimePointList.get(i);
					int t2 = f.discreteTimePointList.get(i + 1);

					FlightSection flightSection = new FlightSection();
					flightSection.flight = f;
					flightSection.startTime = t1;
					flightSection.endTime = t2;

					f.flightSectionList.add(flightSection);
				}
			}
		}
		
		//为每一itinerary生成flight section itinerary
		for (Itinerary ite : itineraryList) {
			//生成替代航班相关的flight section itinerary
			for (Flight f : ite.candidateFlightList) {
				for (FlightSection flightSection : f.flightSectionList) {
					int t1 = flightSection.startTime;
					int t2 = flightSection.endTime;

					FlightSectionItinerary fsi = new FlightSectionItinerary();
					fsi.itinerary = ite;
					fsi.flightSection = flightSection;

					int delay1 = t1 - ite.flight.initialTakeoffT;
					int delay2 = t2 - ite.flight.initialTakeoffT;
					
					if(delay1 >= 0 && delay2 >=0) {
						
						if(delay2 <= 6*60) {
							fsi.unitCost = 0.1;							
						}else if(delay2 <= 24*60 && delay1 >= 6*60) {
							fsi.unitCost = 0.5;
						}else if(delay2 <= 48*60 && delay1 >= 24*60) {
							fsi.unitCost = 1;
						}else {
							System.out.println("delay1:"+delay1+" "+delay2);
						}
					}
					
					if(fsi.unitCost > 1e-6) {
						ite.flightSectionItineraryList.add(fsi);
						flightSection.flightSectionItineraryList.add(fsi);
					}				
				}
			}

			//生成每一个行程初始航班对应的flight section itinerary
			for (FlightSection flightSection : ite.flight.flightSectionList) {
				int t1 = flightSection.startTime;
				int t2 = flightSection.endTime;

				FlightSectionItinerary fsi = new FlightSectionItinerary();
				fsi.itinerary = ite;
				fsi.flightSection = flightSection;
				fsi.unitCost = 0;

				ite.flightSectionItineraryList.add(fsi);
				flightSection.flightSectionItineraryList.add(fsi);
			}
		}
		
		int n2 = 0;
		for(Itinerary ite:itineraryList) {
			n2 += ite.flightSectionItineraryList.size();
		}
		System.out.println("n2:"+n2);
	}

	// 读取转机乘客信息
	public void readTransferPassengerInformation() {
		try {
			Scanner sn = new Scanner(new File("transferpassenger"));

			sn.nextLine();

			while (sn.hasNextLine()) {
				String nextLine = sn.nextLine().trim();
				if (nextLine.equals("")) {
					break;
				}

				Scanner innerSn = new Scanner(nextLine);

				int inFlightID = innerSn.nextInt();
				int outFlightID = innerSn.nextInt();
				int minTurnaroundTime = innerSn.nextInt();
				int volume = innerSn.nextInt();

				Flight inFlight = flightList.get(inFlightID - 1);
				Flight outFlight = flightList.get(outFlightID - 1);

				TransferPassenger transferPassenger = new TransferPassenger();
				transferPassenger.inFlight = inFlight;
				transferPassenger.outFlight = outFlight;
				transferPassenger.minTurnaroundTime = minTurnaroundTime;
				transferPassenger.volume = volume;

				/*inFlight.passengerTransferList.add(transferPassenger);
				outFlight.passengerTransferList.add(transferPassenger);*/

				inFlight.firstPassengerTransferList.add(transferPassenger);
				outFlight.secondPassengerTransferList.add(transferPassenger);
				
				transferPassengerList.add(transferPassenger);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//计算每一个航班的转机乘客和普通乘客
		int n = 0;
		int n1 = 0;
		int n2 = 0;
		for(Flight f:flightList) {
			/*for(TransferPassenger tp:f.passengerTransferList) {
				f.transferPassengerNumber += tp.volume;
			}*/
			
			for(TransferPassenger tp:f.firstPassengerTransferList) {
				f.transferPassengerNumber += tp.volume;
				f.firstTransferPassengerNumber += tp.volume;
			}
			for(TransferPassenger tp:f.secondPassengerTransferList) {
				f.transferPassengerNumber += tp.volume;
				f.secondTransferPassengerNumber += tp.volume;
			}
			
			f.normalPassengerNumber = f.passengerNumber - f.transferPassengerNumber;
			
			n += f.normalPassengerNumber;
			n1 += f.transferPassengerNumber;
			n2 += f.connectedPassengerNumber;
		}
		System.out.println("n:"+n+" "+n1+" "+n2);
	}
}