/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector

import java.io.{File, RandomAccessFile}
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}


object InstanceChecker {
	val logger: Logger = LoggerFactory.getLogger(InstanceChecker.getClass)

	def lockInstance(lockFile: String): Boolean = {
		try {
			val file = new File(lockFile)
			val randomAccessFile = new RandomAccessFile(file, "rw")
			val fileLock = randomAccessFile.getChannel.tryLock()

			if (fileLock != null) {
				Runtime.getRuntime.addShutdownHook(new Thread(() => {
					logger.warn("Shutdown: lockInstance fileLock...")
					try {
						fileLock.release()
						randomAccessFile.close()
						file.delete()
					} catch {
						case ex: Exception =>
							logger.error(s"Unable to remove lock file: $lockFile - $ex")
					}
				}))
				return true
			}
		} catch {
			case ex: Exception =>
				logger.error(s"Unable to create and/or lock file: $lockFile - $ex")
		}
		false
	}

	def printConfigs() = {
		val appConfig = ConfigFactory.load()
		//-- collector/consumer
		val consumerConf = appConfig.getConfig("collector.consumer")
		val consumerTopic = consumerConf.getString("collector_topic")
		val consumerGroupId = consumerConf.getString("collector_group")
		val consumerBrokerInfo = consumerConf.getString("collector_broker_info")
		val consumerCluserInfo = consumerConf.getString("collector_cluster_info")
		val consumerCount = consumerConf.getInt("consumer_count")
		val collectorLockfile = consumerConf.getString("collector_lock_file")

		//-- collector/redis-server
		val mRedisServerIP = appConfig.getString("collector.redis_server.redis_server_ip")

		//-- api-service server
		val api_service_ip_port = appConfig.getString("collector.api_service.server_ip_port")
		val station_layout_info_url = appConfig.getString("collector.api_service.station_layout_info_url")

		//-- collector/producer
		val producerConf = appConfig.getConfig("collector.producer")
		val producerTopic = producerConf.getString("analyzer_topic")
		val producerBrokerInfo = producerConf.getString("analyzer_broker_info")
		val producerAcks = producerConf.getInt("analyzer_acks")

		val JDBConnInfo = appConfig.getString("mariaDB.db.url")
		val dataSourceClassName = appConfig.getString("mariaDB.db.dataSourceClass")
		val dbName = appConfig.getString("mariaDB.db.databaseName")
		val userId = appConfig.getString("mariaDB.db.user")
		val maxConnections = appConfig.getString("mariaDB.maxConnections")
		val numThreads = appConfig.getString("mariaDB.numThreads")

		logger.info("=====================================================")
		logger.info("collector_topic         = " + consumerTopic)
		logger.info("collector_group_id      = " + consumerGroupId)
		logger.info("collector_broker_info   = " + consumerBrokerInfo)
		logger.info("collector_cluster_info  = " + consumerCluserInfo)
		//		logger.info("data_path           = " + CConfig.mDataPath);
		logger.info("collector_lock_file     = " + collectorLockfile)
		logger.info("consumer_count          = " + consumerCount)
		logger.info("---------------------------------------------------")
		logger.info("redis_server_ip         = " + mRedisServerIP)
		logger.info("api_service_ip_port     = " + api_service_ip_port)
		logger.info("station_layout_info_url = " + station_layout_info_url)
		logger.info("---------------------------------------------------")
		logger.info("producer_topic          = " + producerTopic)
		logger.info("producer_broker_info    = " + producerBrokerInfo)
		logger.info("producer_acks           = " + producerAcks)
		logger.info("---------------------------------------------------")
		logger.info("jdbcURL                 = " + JDBConnInfo)
		logger.info("dataSourceClass         = " + dataSourceClassName)
		logger.info("db_name                 = " + dbName)
		logger.info("userId                  = " + userId)
		logger.info("db_max_pool             = " + maxConnections)
		logger.info("=====================================================")
	}
}

