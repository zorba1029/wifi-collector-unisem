/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector

import java.util.concurrent.TimeUnit
import akka.actor.{ActorRef, Props}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.unisem.metrobus.collector.MetrobusCollectorMain.actorSystem
import com.unisem.metrobus.collector.db.DBDataSource
import com.unisem.metrobus.collector.redis.RedisPool
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import akka.pattern.gracefulStop
import com.unisem.metrobus.collector.actors._

import scala.concurrent.Await
import scala.language.postfixOps


object ConsumerContainer {
	def apply(): ConsumerContainer = {
		new ConsumerContainer()
	}
}


class ConsumerContainer() {
	val log: Logger = LoggerFactory.getLogger(classOf[ConsumerContainer])

	//------------------------------------------------------
	//--- Kafka Consumer Setup
	val appConfig: Config = ConfigFactory.load()
	val consumerCount: Int = appConfig.getInt("collector.consumer.consumer_count")

	val stationUpdateActor: ActorRef = actorSystem.actorOf(Props[StationInfoUpdateActor], "stationUpdateActor")

	//-- 2018/6/26, ADDED Producer Actors
	val kafkaProducers: ActorRef = actorSystem.actorOf(RoundRobinPool(consumerCount).props(Props[ProducerActor]), "ProducerActor")

	//-- 2018/9/10, ADDED
	var passengerCountAggregateActor: ActorRef = actorSystem.actorOf(Props[PassengerCountAggregateActor], "PassengerCountAggregateActor")

	//-- 2018/7/23, ADDED Station Passenger Counter Actors
	val stationPassengerCounters: ActorRef = actorSystem.actorOf(RoundRobinPool(consumerCount).props(Props(classOf[StationPassengerCounterActor], passengerCountAggregateActor)), "StationPassengerCounterActor")

	val kafkaConsumers: ActorRef = actorSystem.actorOf(RoundRobinPool(consumerCount).props(Props(classOf[ConsumerActor], stationUpdateActor, kafkaProducers, stationPassengerCounters)), "ConsumerActor")


	//===================================================================
	// shutdown handling
	//-------------------------------------------------------------------
	Runtime.getRuntime.addShutdownHook(new Thread(() => {
		log.info("=============== Start Shutdown ===============")
		implicit val timeout = Timeout(30 seconds)

		//---------------------------------------------------
		//-- 1) close consumers and their threads
		log.info("  -- 1) Stop ConsumerActors")
		val stopConsumers = gracefulStop(kafkaConsumers, 5 seconds)
		Await.result(stopConsumers, 5 seconds)

		log.info("  -- 2) Stop ProducerActors")
		val stopProducers = gracefulStop(kafkaProducers, 5 seconds)
		Await.result(stopProducers, 5 seconds)

		log.info("  -- 3) stop station update actors")
		val stopUpdateActor = gracefulStop(stationUpdateActor, 5 seconds)
		Await.result(stopUpdateActor, 5 seconds)

		log.info("  -- 4) Stop PassengerCounters")
		val stopPassengerCounters = gracefulStop(stationPassengerCounters, 5 seconds)
		Await.result(stopPassengerCounters, 5 seconds)

		log.info("  -- 5) Stop CountAggregateActor")
		val stopCountAggregateActor = gracefulStop(passengerCountAggregateActor, 5 seconds)
		Await.result(stopCountAggregateActor, 5 seconds)

		//---------------------------------------------------
		//-- 2) close Redis Pool
		log.info("  -- 6) close Passenger Visit Redis Pool")
		val poolDoubleRedis = RedisPool.getDoubleRedis()
		if (poolDoubleRedis != null) {
			poolDoubleRedis.destroy()
		}

		log.info("  -- 7) close Passenger Count Redis Pool")
		val poolPassengerCountRedis = RedisPool.getPassengerCountRedis()
		if (poolPassengerCountRedis != null) {
			poolPassengerCountRedis.destroy()
		}

		//---------------------------------------------------
		//-- 3) close DB Pool
		log.info("  -- 8) close DB Pool")
		DBDataSource.close()

		//---------------------------------------------------
		//-- 3) Terminate ActorSystem
		log.info("  -- 8) Terminate ActorSystem...")

		try {
			val terminateFuture = actorSystem.terminate()
			Await.result(terminateFuture, 60 seconds)
			TimeUnit.SECONDS.sleep(1)
		} catch {
			case e: InterruptedException =>
				log.warn(s" * -- ConsumerContainer: InterruptedException - $e")
		}
		log.info("=============== Shutdown: Finished ===============")
	}))
}

