/**
* Copyright (C) 2018 Unisem Inc. <http://www.unisem.co.kr>
*
* Created by Heung-Mook CHOI (zorba)
*/
package com.unisem.metrobus.collector.actors

import java.io.IOException
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.util.Properties
import scala.collection.JavaConverters._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.messages.Messages
import com.unisem.metrobus.collector.messages.Messages.{DeviceRecord, WifiRecordEx}
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.duration._
import scala.language.postfixOps


object ConsumerActor {
	val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
	val DATE_TIME_FORMATTER_MILLI: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
	var consumerActorIdCounter: Int = 0

	def getConsumerActorId(): Int = {
		consumerActorIdCounter = consumerActorIdCounter + 1
		consumerActorIdCounter
	}
}


class ConsumerActor(stationUpdateActor: ActorRef, producerActor: ActorRef, passengerCounterActor: ActorRef) extends Actor with ActorLogging {
	import ConsumerActor._
	implicit val system: ActorSystem = context.system
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val executor = context.dispatcher
	implicit val timeout = Timeout(30 seconds)

	//------------------------------------------------------
	//--- Kafka Consumer Setup
	//------------------------------------------------------
	val appConfig = ConfigFactory.load()

	val consumerConf = appConfig.getConfig("collector.consumer")
	val consumerTopic = consumerConf.getString("collector_topic")
	val consumerGroupId = consumerConf.getString("collector_group")
	val consumerBrokerInfo = consumerConf.getString("collector_broker_info")
	val consumerClusterInfo = consumerConf.getString("collector_cluster_info")
	val consumerCount = consumerConf.getInt("consumer_count")

	val consumerProps: Properties = {
		val props = new Properties
		props.put("bootstrap.servers", consumerBrokerInfo)
		props.put("group.id", consumerGroupId)
		//----props.put("zookeeper.connect", consumerClusterInfo)
		props.put("auto.commit.interval.ms", "1000")
		props.put("key.deserializer", classOf[StringDeserializer])
		props.put("value.deserializer", classOf[StringDeserializer])
		props
	}

	val consumer = new KafkaConsumer[String, String](consumerProps)
	val topicList: List[String] = List[String](consumerTopic)
	val consumerActorId: Int = getConsumerActorId()

	var stationNameScheduler: Cancellable = _
	var stationInfoScheduler: Cancellable = _

	//-- format: Map(key,value): [sensor_id, (sensor_type, station_id)]
	var sensorToStationMap = scala.collection.immutable.Map.empty[String, (String,String)]
	var stationIdNameMap = scala.collection.immutable.Map.empty[String, String]

	log.debug(s"ActorId=[$consumerActorId], topicList=[$consumerTopic], consumerGroupId=[$consumerGroupId], clusterInfo=[$consumerClusterInfo]")


	override def preStart: Unit = {
		log.debug(s"ActorId=[$consumerActorId] - [*] ~~>> preStart() ")
		stationNameScheduler = context.system.scheduler.schedule(2 seconds, 10 minutes, self, Messages.RequestStationIdNameList)
		stationInfoScheduler = context.system.scheduler.schedule(3 seconds, 10 minutes, self, Messages.RequestSensorStationInfo)
		context.system.scheduler.scheduleOnce(5 seconds, self, Messages.StartConsumer)
	}


	override def postStop: Unit = {
		stationNameScheduler.cancel()
		stationInfoScheduler.cancel()

		consumer.wakeup()
		consumer.unsubscribe()
		consumer.close()

		log.debug(s"ActorId=[$consumerActorId] - [*] ~~>> postStop() - DONE")
	}


	override def receive: Receive = {
		case Messages.SubscribeConsumer =>
			log.debug(s"ActorId=[$consumerActorId] - Messages.SubscribeConsumer -- ")
			consumer.subscribe(topicList.asJavaCollection)

		case Messages.StartConsumer =>
			log.debug(s"ActorId=[$consumerActorId] - Messages.StartConsumer == ${topicList.head}")
			consumer.subscribe(topicList.asJavaCollection)
			self ! Messages.ConsumeRecord

		case Messages.ConsumeRecord =>
			//--log.debug(s"ActorId=[$consumerActorId] - Messages.ConsumeRecord ==")
			try {
				val records: ConsumerRecords[String,String] = consumer.poll(3000)
				self ! Messages.ProcessRecords(records)
			} catch {
				case wakeupEx: WakeupException =>
					log.error(s"[$$] ActorId=[$consumerActorId] Kafka Wakeup Exception - consumer closed...$wakeupEx")
					consumer.close()
				case _: Throwable =>
					log.error(s"[$$] ActorId=[$consumerActorId] Kafka Exception")
			}

		case Messages.ProcessRecords(records: ConsumerRecords[String,String]) =>
			//--log.debug(s"ActorId=[$consumerActorId] - Messages.ProcessRecords ==")
			if (!records.isEmpty()) {
				processRecords(records)
			}
			self ! Messages.ConsumeRecord

		case Messages.RequestSensorStationInfo =>
			log.debug(s"ActorId=[$consumerActorId] - Messages.RequestSensorStationInfo ---->>")
			stationUpdateActor ! Messages.RequestSensorStationInfo

		case Messages.ResponseSensorStationInfo(map) =>
			//--log.debug(s"[*] ActorId=[$consumerActorId] GOT ResponseSensorStationInfo(map)")
			sensorToStationMap = map
			log.debug(s"[*] ActorId=[$consumerActorId] <<-- GOT ResponseSensorStationInfo - sensorToStationMap")

		//-- 2018/8/30, ADDED -------------------
		case Messages.RequestStationIdNameList =>
			log.debug(s"ActorId=[$consumerActorId] - Messages.ResponseStationIdNameInfo ---->>")
			stationUpdateActor ! Messages.RequestStationIdNameList

		//-- 2018/8/30, ADDED -------------------
		case Messages.ResponseStationIdNameList(map) =>
			//--log.debug(s"[*] ActorId=[$consumerActorId] GOT ResponseStationIdNameInfo(map) = {$map}")
			stationIdNameMap = map
			log.debug(s"[*] ActorId=[$consumerActorId] <<-- GOT ResponseStationIdNameInfo - sensorToStationMap")

		case _ =>
			log.debug(s"[$$][$consumerActorId] --- un-handled message ==")
	}


	protected def processRecords(records: ConsumerRecords[String,String]): Unit = {
		//		records.forEach(record => log.debug(s"[*] ActorId=[$consumerActorId] Incoming [Kafka] WifiRecord = topic[${record.topic}], " +
		//																			s"partition[${record.partition}], offset[${record.offset}], value = {record.value}"))
		records.forEach(record => process(record))
	}


	//-- msgBody = {
	//    "type":"dev_event",
	//    "sensorID":"00012",
	//    "datetime":"1527820594299",
	//    "events":"02:1E:31:79:E6:E5,-86,2%0AB8:27:EB:EB:FB:FD,-39,0%0AE4:02:9B:58:45:44,-42,0%0A64:E5:99:7F:AA:58,-77,0%0A7C:7A:91:85:F2:91,-79,1",
	//    "version":"S200-S"
	// }
	protected def process(record: ConsumerRecord[String,String]): Unit = try {
		val msgBody = record.value
		//--log.debug(s"[*] ActorId=[$consumerActorId]      [UnWrapped] WifiRecord(msgBody) -- $msgBody")

		val recList = tokenizeDeviceListEx(consumerActorId, msgBody)
		//-- 2016/6/25, ADDED & MODIFIED: send recordLost to Producer Actor
		//--log.debug(s"ActorId=[$consumerActorId] --->> Messages.SendToProducer ==")
		producerActor ! Messages.SendToProducer(consumerActorId, recList)
	} catch {
		case e1: IOException =>
			log.warning(s"[$$] ActorId=[$consumerActorId] - IOException - ${e1.getMessage}")
		case th: Exception =>
			log.error(s"[$$] ActorId=[$consumerActorId] - Exception is occurred - ${th.getMessage}")
	}


	private def tokenizeDeviceListEx(actorId: Int, messageBody: String): List[DeviceRecord] = {
		import io.circe.generic.auto._
		import io.circe.parser.decode
		val emptyDevRecordList = List[DeviceRecord]()
		val inputData = new StringBuilder

		//------------------------------
		//-- 2018/6/1, used circe Json to parse Json string
		val wifiEventsRecord = decode[WifiRecordEx](messageBody) match {
			case Right(wifiEventsRec: WifiRecordEx) =>
				log.debug(s"[*] ActorId=[$consumerActorId] Incoming WifiRecordEx = $wifiEventsRec")
				wifiEventsRec
			case Left(failure) =>
				log.warning(s"[*] ActorId=[$consumerActorId] ERROR: Incoming WifiRecordEx = $failure")
				return emptyDevRecordList
		}

		//------------------------------
		//-- sensorID
		//-- 2018/5/29, MODIFIED - used circe Json
		val sensorID = wifiEventsRecord.sensorID match {
			case Some(sensorID) =>
				inputData.append(s"ActorId=[$consumerActorId] SENSOR_ID: $sensorID")
				sensorID
			case None =>
				log.error(s"[$$] ActorId=[$consumerActorId] SENSOR_ID: ERROR")
				return emptyDevRecordList
		}

		//------------------------------
		//-- datetime
		//-- 2018/5/29, MODIFIED - used circe Json
		val localDTStr = wifiEventsRecord.`datetime` match {
			case Some(tsStr) =>
				val timestamp = new Timestamp(tsStr.toLong)
				val localDT = timestamp.toLocalDateTime.plusHours(appConfig.getInt("collector.adjust_sensor_time_delta"))
				val localDTStr = localDT.format(DATE_TIME_FORMATTER)
				inputData.append(s", localDT: $localDT")
				inputData.append(s", localDTStr: $localDTStr")
				//--log.debug(s"ActorId=[$consumerActorId] --------- localDT: $localDT, localDTStr: $localDTStr")
				localDTStr
			case None =>
				log.error(s"[$$] ActorId=[$consumerActorId] - NOW_TIME: ERROR")
				return emptyDevRecordList
		}

		//------------------------------
		//-- sensorVersion
		//-- 2018/4/24, ADDED 'version' field (for S200 sensor only)
		//-- 2018/5/29, MODIFIED - used circe Json
		val sensorVersion = wifiEventsRecord.version match {
			case Some(version) => version
			//-----case None => "S100"
			case None => "S200-S"
		}
		inputData.append(s", sensorVersion: $sensorVersion")

		//------------------------------
		//-- device list
		//--log.debug(s"ActorId=[$consumerActorId] --------- events: ${wifiEventsRecord.events}")
		val devList = wifiEventsRecord.events match {
			case Some(eventList) =>
				//--log.debug(s"ActorId=[$consumerActorId] --------- eventList: ${eventList}")
				eventList
			case None =>
				return emptyDevRecordList
		}

		//--------------------------------------------
		//-- tokenize dev addresses from event list
		//--------------------------------------------
		//-- Option(tuple2(sensorType, stationId))
		val tuple2 = sensorToStationMap.get(sensorID)
		//--log.debug(s"ActorId=[$consumerActorId]  sensorToStationMap($sensorID) = <$tuple2>")

		tuple2 match {
			case Some((sensorType,stationId)) =>
				val recList = sensorVersion match {
					case "S200-S" =>
						devList.map(item => {
							val stationName = stationIdNameMap.get(stationId)
							val devRec = DeviceRecord(sensorID, sensorType, stationId, stationName, item.MAC, item.RSSI, item.DSSTATUS, localDTStr)
							devRec
						})
						.filter(_.isValid)

					case _ =>
						val emptyList = List[DeviceRecord]()
						emptyList
				}
				//----log.debug(s"ActorId=[$consumerActorId] ------>> Device List = $inputData, Dev Count = ${recList.length}")
				//--recList.foreach(rec => log.debug(s"--> Device = ${rec}"))

				//-----------------------------------
				//-- 2018/7/23, ADDED sensorType == platform/gate (P/T), send the list to StationPassengerCounterActor
				//-- 2018/8/30, sensorType = P/T has been changed in DB table (SENSOR table)
				sensorType match {
					case "P" =>
						passengerCounterActor ! Messages.PlatformWifiRecordList(recList)
					case "T" =>
						passengerCounterActor ! Messages.PlatformWifiRecordList(recList)
					case _ =>
						// Nothing
				}
				recList

			case None =>
				log.error(s"[$$] ActorId=[$consumerActorId] ---->> sensorToStationMap.get(sensorId=$sensorID): NOT Found")
				emptyDevRecordList
		}
	}
}


//	mysql> select * from STATION;
//	+-----------+-----------------------------+
//	| StationId | StationName                 |
//	+-----------+-----------------------------+
//	|         1 | Sogutlucesme                |
//	|         2 | Fikirtepe                   |
//	|         3 | Uzuncayir                   |
//	|         4 | Acibadem                    |
//	|         5 | Altunizade                  |
//	|         6 | Burhaniye                   |
//	|         7 | 15-temmuz-sehitler-koprusu  |
//	|         8 | Zincirlikuyu                |
//	|         9 | Mecidiyekoy                 |
//	|        10 | Caglayan                    |
//	|        11 | Okmeydani-hastane           |
//	|        12 | Darulaceze-perpa            |
//	|        13 | Okmeydani                   |
//	|        14 | Halicioglu                  |
//	|        15 | Ayvansaray-eyup-sultan      |
//	|        16 | Edirnekapi                  |
//	|        17 | Bayrampasa-maltepe          |
//	|        18 | Topkapi                     |
//	|        19 | Cevizlibag                  |
//	|        20 | Merter                      |
//	|        21 | Zeytinburnu                 |
//	|        22 | Incirli                     |
//	|        23 | Bahcelievler                |
//	|        24 | Sirinevler                  |
//	|        25 | Yenibosna                   |
//	|        26 | Sefakoy                     |
//	|        27 | Besyol                      |
//	|        28 | Florya                      |
//	|        29 | Cennet-mahallesi            |
//	|        30 | Kucukcekmece                |
//	|        31 | B-sehir-bld-sosyal-tes      |
//	|        32 | Sukrubey                    |
//	|        33 | Avcilar-merkez-univ-kampusu |
//	|        34 | Cihangir-univ-mahallesi     |
//	|        35 | Mustafa-kemal-pasa          |
//	|        36 | Saadetdere-mahallesi        |
//	|        37 | Haramidere-sanayi           |
//	|        38 | Haramidere                  |
//	|        39 | Guzelyurt                   |
//	|        40 | Beylikduzu                  |
//	|        41 | Beylikduzu-belediyesi       |
//	|        42 | Cumhuriyet-mahallesi        |
//	|        43 | Hadimkoy                    |
//	|        44 | Beylikduzu-sondurak         |
//	+-----------+-----------------------------+
//	44 rows in set (0.00 sec)

//	private def tokenizeDeviceList(actorId: Int, messageBody: String): List[DeviceRecord] = {
//		import io.circe.generic.auto._
//		import io.circe.parser.decode
//		val emptyDevRecordList = List[DeviceRecord]()
//		val inputData = new StringBuilder
//
//		//------------------------------
//		//-- 2018/6/1, used circe Json to parse Json string
//		val wifiEventsRecord = decode[WifiRecord](messageBody) match {
//			case Right(wifiEventsRec: WifiRecord) =>
//				log.debug(s"[*] ActorId=[$consumerActorId] Incoming WifiRecord = $wifiEventsRec")
//				wifiEventsRec
//			case Left(failure) =>
//				log.warning(s"[*] ActorId=[$consumerActorId] ERROR: Incoming WifiRecord = $failure")
//				return emptyDevRecordList
//		}
//
//		//------------------------------
//		//-- sensorID
//		//-- 2018/5/29, MODIFIED - used circe Json
//		val sensorID = wifiEventsRecord.sensorID match {
//			case Some(sensorID) => inputData.append(s"ActorId=[$consumerActorId] SENSOR_ID: $sensorID")
//				sensorID
//			case None =>
//				log.error(s"[$$] ActorId=[$consumerActorId] SENSOR_ID: ERROR")
//				return emptyDevRecordList
//		}
//
//		//------------------------------
//		//-- datetime
//		//-- 2018/5/29, MODIFIED - used circe Json
//		val localDTStr = wifiEventsRecord.`datetime` match {
//			case Some(tsStr) =>
//				val timestamp = new Timestamp(tsStr.toLong)
//				val localDT = timestamp.toLocalDateTime.plusHours(appConfig.getInt("collector.adjust_sensor_time_delta"))
//				val localDTStr = localDT.format(DATE_TIME_FORMATTER)
//				//	val localDT2 = LocalDateTime.parse(tsStr)
//				//	val localDTStr = localDT.format(DATE_TIME_FORMATTER)
//				inputData.append(s", localDT: $localDT")
//				inputData.append(s", localDTStr: $localDTStr")
//				//--log.debug(s"ActorId=[$consumerActorId] --------- localDT: $localDT, localDTStr: $localDTStr")
//				localDTStr
//			case None =>
//				log.error(s"[$$] ActorId=[$consumerActorId] - NOW_TIME: ERROR")
//				return emptyDevRecordList
//		}
//
//		//------------------------------
//		//-- sensorVersion
//		//-- 2018/4/24, ADDED 'version' field (for S200 sensor only)
//		//-- 2018/5/29, MODIFIED - used circe Json
//		val sensorVersion = wifiEventsRecord.version match {
//			case Some(version) => version
//			case None => "S100"
//		}
//		inputData.append(s", sensorVersion: $sensorVersion")
//
//		//------------------------------
//		//-- device list
//		val devList = wifiEventsRecord.events match {
//			case Some(eventList) =>
//				val decodeEvtListStr = URLDecoder.decode(eventList, "UTF-8")
//				decodeEvtListStr.split("\\r?\\n")
//			case None =>
//				return emptyDevRecordList
//		}
//
//		//--------------------------------------------
//		//-- tokenize dev addresses from event list
//		//--------------------------------------------
//		val tuple2 = sensorToStationMap.get(sensorID)  //-- Option(tuple2(sensorType, stationId))
//		log.debug(s"ActorId=[$consumerActorId]  <sensor-type,station-id> : $tuple2")
//
//		tuple2 match {
//			case Some((sensorType,stationId)) =>
//				//-- 2018/05/25 - replace if with match and use case class DeviceRecord instead of normal class
//				val recList = sensorVersion match {
//					case "S200-S" =>
//						devList.map(_.split(","))
//								.map(items => DeviceRecord(sensorID, sensorType, stationId, items(0), items(1).toInt, items(2).toInt, localDTStr))
//								.filter(_.isValid)
//								.toList
//					case _ =>
//						devList.map(_.split(","))
//								.map(items => {
//									val offset = if (items.length == 12) 1 else 0
//									val mac = items(0)
//									val power = items(4 + offset).toInt
//									val dsStatus = items(5 + offset).toInt
//									(mac, power, dsStatus)
//								})
//								.map(t3 => DeviceRecord(sensorID, sensorType, stationId, t3._1, t3._2, t3._3, localDTStr))
//								.filter(_.isValid)
//								.toList
//					//--filter {(rec: DeviceRecord) => rec.isValidWithOnlyCellphone} toList
//				}
//				log.debug(s"ActorId=[$consumerActorId] ------>> Device List = $inputData, Dev Count = ${recList.length}")
//				//--recList.foreach(rec => log.debug(s"ActorId=[$consumerActorId] -> Device = ${rec}"))
//
//				//-----------------------------------
//				//-- 2018/7/23, ADDED sensorType == platform, send the list to StationPassengerCounterActor
//				sensorType match {
//					case "platform" =>
//						passengerCounterActor ! Messages.PlatformWifiRecordList(recList)
//					case "gate" =>
//						passengerCounterActor ! Messages.PlatformWifiRecordList(recList)
//					case _ =>
//					// Nothing
//				}
//				recList
//
//			case None =>
//				log.error(s"[$$] ActorId=[$consumerActorId] ---->> sensorToStationMap.get(sensorId=$sensorID): NOT Found")
//				emptyDevRecordList
//		}
//	}


