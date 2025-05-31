/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.messages

import org.apache.kafka.clients.consumer.ConsumerRecords

object Messages {
	case object SubscribeConsumer
	case object StartConsumer
	case object ConsumeRecord
	case object ShutDownConsumer
	case class ProcessRecords(records: ConsumerRecords[String,String])
	case class UpdateStationInfo()
	case object RequestSensorStationInfo
	case class ResponseSensorStationInfo(map: scala.collection.immutable.Map[String, (String,String)])
	//-- 2018/6/25, ADDED
	case class SendToAnalyzer(actorId: Int, devList: List[DeviceRecord])
	//-- 2018/8/1, ADDED
	case class SendToProducer(actorId: Int, devList: List[DeviceRecord])

	//-- 2018/8/30
	case object UpdateStationIdNameList
	//	case object RequestStationIdNameInfo
	case object RequestStationIdNameList
	case class ResponseStationIdNameList(map: scala.collection.immutable.Map[String,String])

	//--------------------------------
	// 2018/6/1, ADDED
	//-- msgBody = {
	//    "type":"dev_event",
	//    "sensorID":"00012",
	//    "datetime":"1527820594299",
	//    "events":"02:1E:31:79:E6:E5,-86,2%0AB8:27:EB:EB:FB:FD,-39,0%0AE4:02:9B:58:45:44,-42,0%0A64:E5:99:7F:AA:58,-77,0%0A7C:7A:91:85:F2:91,-79,1",
	//    "version":"S200-S"
	// }

	case class WifiRecord(sensorID: Option[String], `type`: Option[String], `datetime`: Option[String], events: Option[String], version: Option[String] = None)

	//-- 2018/8/21, ADDED
	case class DevProps(MAC: String, RSSI: Int, DSSTATUS: Int)
	case class WifiRecordEx(sensorID: Option[String], `type`: Option[String] = None, `datetime`: Option[String], events: Option[List[DevProps]], version: Option[String] = None)

	//--------------------------------
	// 2018/5/25, modified DeviceRecord class to case class
	//-- 2018/3/31, ADDED stationName: Option[String] = None,
	//----------
	case class DeviceRecord(sensorID: String, sensorType: String, stationID: String, stationName: Option[String] = None, deviceID: String, power: Int, dsStatus: Int, datetime: String) {
		def getKey: String = {
			stationID ++ "#" ++ deviceID
		}

		def getValue: String =  {
			datetime
		}

		def isValid: Boolean = {
			if (sensorID != null && deviceID != null && stationID != null && datetime != null) {
				true
			} else {
				false
			}
		}

		//-- 2018/4/24, ADDED check dsStatus <= 1 : drop wireless router (dsStatus > 1)
		def isValidWithOnlyCellphone: Boolean = {
			if (sensorID != null && deviceID != null && stationID != null && datetime != null
					&& dsStatus <= 1) {
				true
			} else {
				false
			}
		}

		override def toString: String = {
			"DeviceRecord = {" +
					"sensorID='" + sensorID + '\'' +
					", sensorType='" + sensorType + '\'' +
					", stationID='" + stationID + '\'' +
					", stationName='" + stationName + '\'' +
					", deviceID='" + deviceID + '\'' +
					", power='" + power + '\'' +
					", dsStatus='" + dsStatus + '\'' +
					", datetime='" + datetime + '\'' +
					'}'
		}
	}

	//----------------------------
	// 2018/7/23, ADDED
	case class PlatformWifiRecordList(recList: List[DeviceRecord])
	case object PassengerCountRequest

	case class PassengerCountData(devRec: DeviceRecord)
	case object PassengerCount30Seconds
	case object PassengerCount1Minute
	case object PassengerCount5Minutes
	case object PassengerCountAggregate
}
