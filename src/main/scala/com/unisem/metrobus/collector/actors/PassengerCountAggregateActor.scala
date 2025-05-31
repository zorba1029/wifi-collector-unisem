package com.unisem.metrobus.collector.actors

import java.util.Properties

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.messages.Messages
import com.unisem.metrobus.collector.messages.Messages.DeviceRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

import scala.concurrent.duration._



class PassengerCountAggregateActor extends Actor with ActorLogging {
	//	import PassengerCountAggregateActor._
	implicit val system = context.system
	implicit val materializer = ActorMaterializer()
	implicit val executor = context.dispatcher
	implicit val timeout = Timeout(30 seconds)

	//--------------------------------------------------------------
	//-- 2018/8/30, zorba ADDED: device_log_topic topic producer
	//------------------------------------------------------
	//--- Kafka Producer Setup and Create [for device log]
	//------------------------------------------------------
	val appConfig = ConfigFactory.load()
	val producerConf = appConfig.getConfig("collector.producer")

	//------------------------------------------------------
	//-- device_log_topic
	val producerTopic = producerConf.getString("device_log_topic")
	val producerBrokerInfo = producerConf.getString("analyzer_broker_info")
	val producerAcks = producerConf.getInt("analyzer_acks")

	val producerProps = {
		val props = new Properties
		props.put("bootstrap.servers", producerBrokerInfo)
		props.put("acks", producerAcks.toString)
		//----props.put("block.on.buffer.full", "true");
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
		props
	}
	val producer = new KafkaProducer[String, String](producerProps)

	var aggregateScheduler: Cancellable = _
	type DeviceCountMap = scala.collection.mutable.HashMap[String, Int]
	private var stationMap = scala.collection.mutable.HashMap.empty[String, DeviceCountMap]


	override def preStart = {
		log.debug(s"- [*] ~~>> preStart() ")
		QuartzSchedulerExtension(system).schedule("Every30Seconds", self, Messages.PassengerCount30Seconds)
	}

	override def postStop = {
		producer.flush()
		producer.close()
		log.debug(s"- [*] ~~>> postStop() - DONE")
	}


	override def receive: Receive = {
		case Messages.PassengerCountData(devRec: DeviceRecord) =>
			val devCountMap = stationMap.getOrElse(devRec.stationID, {
				stationMap += (devRec.stationID -> scala.collection.mutable.HashMap.empty[String, Int])
				stationMap.getOrElse(devRec.stationID, None)
			})

			devCountMap match {
				case devMap: DeviceCountMap =>
					val countOpt = devMap.get(devRec.deviceID)
					countOpt match {
						case Some(count) =>
							devMap(devRec.deviceID) = count + 1
							//--log.debug(s"--- [*] PassengerCountData: * EXIST Device-ID(${devRec.stationID}, ${devRec.deviceID}, ${devRec.datetime}): count = $count + 1 <-- ++++++++++")
						case None =>
							devMap += (devRec.deviceID -> 1)
							//--log.debug(s"--- [*] PassengerCountData: + NEW   Device-ID(${devRec.stationID}, ${devRec.deviceID}, ${devRec.datetime}): count = 1 <--- 111111")
							//-- DONE: zorba - send each new DeviceRecord to Analyzer (kafka - deviceLog topic)
							produceToAnalyzer(devRec)
					}
				case None =>
					stationMap += (devRec.stationID -> scala.collection.mutable.HashMap.empty[String, Int])
					log.debug(s"--- [*] PassengerCountData: +++ NEW DeviceMAP(${devRec.stationID}) Created..")
			}

		case Messages.PassengerCount30Seconds =>
			log.debug(s"--- [*****] =========== Passenger Count - 30 Seconds Aggregate: ==========================================")
			var totalCount = 0
			for ((stationId, devMap) <- stationMap) {
				var validItemCount = 0
				for ((devId, count) <- devMap) {
					if (count >= 1) {
						validItemCount += 1
						totalCount += 1
						//--log.debug(s"--- [*****] PassengerCount - 30 Seconds Aggregate: (stationId: devId, Count) = (${stationId}: ${devId}, $count}) ****** ")
					}
				}
				log.debug(s"--- [*****] --------: (stationId: valid Count) = (${stationId}: ${validItemCount}) ")
				devMap.clear()
			}
			log.debug(s"--- [*****] ----------- PassengerCount - 30 Seconds Aggregate: Total Valid Count = $totalCount -----------------------")
			stationMap.clear()

		case _ =>
			// No Action
	}

	//-- 2018/8/30, ADDED: send DeviceRecord to Kafka[topic=topic-device-log-trial]
	private def produceToAnalyzer(record: DeviceRecord): Unit = {
		import io.circe.generic.auto._
		import io.circe.syntax._
		//-- 2018/5/25, 2108/6/1, use circe
		val jsonString = record.asJson.toString

		//--log.debug(s"[*] AggregateActor [*] produceToAnalyzer() - DeviceRecord(JSON) = $jsonString")
		//--log.debug(s"[*] AggregateActor [*] SEND NEW RECORD -> Analyzer : [${record.stationID}, ${record.deviceID}, ${record.datetime}]")

		producer.send(new ProducerRecord[String, String](producerTopic, jsonString), (metadata: RecordMetadata, exception: Exception) => {
			def sendCallback(metadata: RecordMetadata, ex: Exception): Unit = {
				if (metadata != null) {
					log.debug(s"[*] AggregateActor Producer CB - SUCCESS: partition(${metadata.partition()}), offset(${metadata.offset()})")
				} else {
					log.error(s"[*] AggregateActor Producer CB - **ERROR: FAILED to Send To Analyzer. exception = $ex")
				}
			}

			sendCallback(metadata, exception)
		})
	}
}
