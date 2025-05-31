package com.unisem.metrobus.collector.actors

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.messages.Messages
import com.unisem.metrobus.collector.messages.Messages._
import com.unisem.metrobus.collector.redis._

import scala.concurrent.duration._
import scala.language.postfixOps


object StationPassengerCounterActor {
	val DATE_ONLY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
	val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
	private var passengerCounterActorIdCounter: Int = 0

	private def getStationPassengerCounterActorId(): Int = {
		passengerCounterActorIdCounter = passengerCounterActorIdCounter + 1
		passengerCounterActorIdCounter
	}
}

class StationPassengerCounterActor(countAggregateActor: ActorRef) extends Actor with ActorLogging {
	import StationPassengerCounterActor._
	implicit val system = context.system
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val executor = context.dispatcher
	implicit val timeout = Timeout(30 seconds)

	val appConfig = ConfigFactory.load()

	private var passengerCountScheduler: Cancellable = _
	var redisHandler: RedisHandler = new PassengerCounterRedisHandler()
	private val passengerCounterActorId: Int = getStationPassengerCounterActorId()


	override def preStart = {
		log.debug(s"ActorId=[$passengerCounterActorId] - [*] ~~>> preStart() ")
		passengerCountScheduler = context.system.scheduler.schedule(3 seconds, 10 minutes, self, Messages.PassengerCountRequest)
	}

	override def postStop = {
		log.debug(s"ActorId=[$passengerCounterActorId] - [*] ~~>> postStop() - DONE")
	}


	override def receive: Receive = {
		case Messages.PlatformWifiRecordList(recList: List[DeviceRecord]) =>
			log.debug(s"ActorId=[$passengerCounterActorId] - [*] ~~>> Messages.PlatformWifiRecordList, len = ${recList.length}")
			//--log.debug(s"  len = ${recList.length}, DeviceList = ${recList}")
			putIntoRedis(passengerCounterActorId, recList)

		case Messages.PassengerCountRequest =>
			log.debug(s"ActorId=[$passengerCounterActorId] - [*] ~~>> Messages.PassengerCountRequest")

		case _ =>
			//-- No Action
	}

	private def putIntoRedis(actorId: Int, devList: List[DeviceRecord]): Unit = {
		try {
			val jedis = RedisPool.getPool().getResource

			try {
				val currentDateTime = LocalDateTime.now()
				val adjustedDT = currentDateTime.plusHours(appConfig.getInt("collector.debug_timezone_delta"))
				val nowTimeMinus30 = adjustedDT.minusMinutes(30)
				//--log.debug(s"  nowTime = ${nowTime}")
				devList
						.filter(devRec => {
							val devTS = LocalDateTime.parse(devRec.datetime, DATE_TIME_FORMATTER)
							val withinDuration = devTS.isAfter(nowTimeMinus30)
							//	if (withinDuration) {
							//		log.debug(s"  (WITHIN duration) = (${devTS}, ${adjustedDT})")
							//	} else {
							//		log.debug(s"  (  OVER duration) = (${devTS}, ${adjustedDT})")
							//	}
							withinDuration
						})
						.filter(redisHandler.process(actorId, jedis, _))
						.foreach(sendToAggregator)
			} catch {
				case e: Exception =>
					log.error(s"[$$] ActorId=[$actorId] REDIS: 1> ERROR: FAILED to get Redis Pool instance -> System Exit. - $e")
					log.error(s"[$$] ActorId=[$actorId] REDIS: 1> Check if redis-server /usr/local/etc/redis.conf is UP & RUNNING>")
			} finally {
				if (jedis != null) {
					jedis.close()
					//--log.debug(s"[$$] ActorId=[$actorId] REDIS: ------- jedis.close() ------")
				}
			}
		} catch {
			case e: Exception =>
				log.error(s"[$$] ActorId=[$actorId] REDIS: 2> ERROR: FAILED to get Redis Pool instance -> System Exit. - $e")
				log.error(s"[$$] ActorId=[$actorId] REDIS: 2> Check if redis-server /usr/local/etc/redis.conf is UP & RUNNING>")
		}
	}

	private def sendToAggregator(record: DeviceRecord): Unit = {
		countAggregateActor ! Messages.PassengerCountData(record)
		//--log.debug(s"[$$] ActorId=[$passengerCounterActorId] ** sendToAggregator() -> (${record.sensorID}: ${record.stationID}, ${record.deviceID}, ${record.datetime})")
	}
}
