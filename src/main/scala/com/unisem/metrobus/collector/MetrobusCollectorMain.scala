/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.db.DBDataSource
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor


object MetrobusCollectorMain extends App {
	implicit val actorSystem: ActorSystem = ActorSystem("metrobus-collector")
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
	private val logger = LoggerFactory.getLogger(MetrobusCollectorMain.getClass)

	if (!InstanceChecker.lockInstance("/tmp/lock_collector")) {
		logger.error(s"Failed to lock Instance.")
		System.exit(1)
	}
	InstanceChecker.printConfigs()

	//-- So far, Collector does not use DB
	//--	DBDataSource()

	ConsumerContainer()

	logger.info("======= START : MetrobusCollectorMain =========")
}
