/**
	* Copyright (C) 2018 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.actors

import java.util.Properties

import akka.actor.{Actor, ActorLogging}
import akka.stream.{ActorMaterializer}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.messages.Messages
import com.unisem.metrobus.collector.messages.Messages.DeviceRecord
import com.unisem.metrobus.collector.redis.{DoubleRedisHandler, RedisHandler, RedisPool}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}

import scala.concurrent.duration._


object ProducerActor {
	var producerActorCounter: Int = 100

	def getProducerActionId() = {
		producerActorCounter = producerActorCounter + 1
		producerActorCounter
	}
}

class ProducerActor extends Actor with ActorLogging {
	import ProducerActor._
	implicit val system = context.system
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val executor = context.dispatcher
	implicit val timeout = Timeout(30 seconds)

	//------------------------------------------------------
	//--- Kafka Producer Setup and Create
	//------------------------------------------------------
	val appConfig = ConfigFactory.load()
	val producerConf = appConfig.getConfig("collector.producer")
	val producerTopic = producerConf.getString("analyzer_topic")
	val producerBrokerInfo = producerConf.getString("analyzer_broker_info")
	val producerAcks = producerConf.getInt("analyzer_acks")

	val producerProps: Properties = {
		val props = new Properties
		props.put("bootstrap.servers", producerBrokerInfo)
		props.put("acks", producerAcks.toString)
		//----props.put("block.on.buffer.full", "true");
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
		props
	}

	val producer = new KafkaProducer[String, String](producerProps)
	private val producerActorId: Int = getProducerActionId()
	var redisHandler: RedisHandler = new DoubleRedisHandler()


	override def preStart = {
		log.debug(s"ActorId=[$producerActorId] - [*] ~~>> preStart() ")
	}

	override def postStop = {
		producer.flush()
		producer.close()

		log.debug(s"ActorId=[$producerActorId] - [*] ~~>> postStop() - DONE")
	}

	override def receive: Receive = {
		case Messages.SendToProducer(actorId: Int, devList: List[DeviceRecord]) =>
			log.debug(s"ActorId=[$producerActorId] <<--- Messages.SendToProducer ==")
			sendToAnalyzer(actorId, devList)

		case _ =>
			log.debug(s"[$$][$producerActorId] : Messages.SendToProducer => un-handled message ==")
	}

	private def sendToAnalyzer(actorId: Int, devList: List[DeviceRecord]): Unit = {
		try {
			val jedis = RedisPool.getPool().getResource

			try {
				devList.filter(_.isValid)
						.filter(redisHandler.process(producerActorId, jedis, _))
						.foreach(produceToAnalyzer(_))
			} catch {
				case e: Exception =>
					log.error(s"[$$] ActorId=[$producerActorId] REDIS: 1> ERROR: FAILED to get Redis Pool instance -> System Exit. - $e")
					log.error(s"[$$] ActorId=[$producerActorId] REDIS: 1> Check if redis-server /usr/local/etc/redis.conf is UP & RUNNING>")
			} finally {
				if (jedis != null) {
					jedis.close()
				}
			}
		} catch {
			case e: Exception =>
				log.error(s"[$$] ActorId=[$producerActorId] REDIS: 2> ERROR: FAILED to get Redis Pool instance -> System Exit. - $e")
				log.error(s"[$$] ActorId=[$producerActorId] REDIS: 2> Check if redis-server /usr/local/etc/redis.conf is UP & RUNNING>")
		}
	}

	private def produceToAnalyzer(record: DeviceRecord): Unit = {
		import io.circe.generic.auto._
		import io.circe.syntax._
		//-- 2018/5/25, 2108/6/1, use circe
		val jsonString = record.asJson.toString

		//--log.debug(s"[*] ActorId=[$producerActorId] [*] produceToAnalyzer() - DeviceRecord(JSON) = $jsonString")
		//--log.debug(s"[*] ActorId=[$producerActorId] [*] SEND NEW RECORD -> Analyzer : [${record.stationID}, ${record.deviceID}, ${record.datetime}]")

		producer.send(new ProducerRecord[String, String](producerTopic, jsonString), (metadata: RecordMetadata, exception: Exception) => {
			def sendCallback(metadata: RecordMetadata, ex: Exception): Unit = {
				if (metadata != null) {
					log.debug(s"[*] ActorId=[$producerActorId] Producer CB - SUCCESS: partition(${metadata.partition()}), offset(${metadata.offset()})")
				} else {
					log.error(s"[*] ActorId=[$producerActorId] Producer CB - **ERROR: FAILED to Send To Analyzer. exception = $ex")
				}
			}

			sendCallback(metadata, exception)
		})
	}
}
