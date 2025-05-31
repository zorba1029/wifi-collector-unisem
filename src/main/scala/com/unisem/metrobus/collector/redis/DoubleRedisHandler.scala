/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.redis

import com.unisem.metrobus.collector.messages.Messages._
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis


class DoubleRedisHandler extends RedisHandler {
	val log = LoggerFactory.getLogger(classOf[DoubleRedisHandler])
	//-- DO NOT DELETE BELOW VALUES
	val GATE_FIELD_NAME = "gate_field"
	val PLATFORM_FIELD_NAME = "platform_field"
	val KEY_PREFIX_GATE = "gate##"
	val KEY_PREFIX_PLATFORM = "platform##"

	override def process(actorIndex: Int, jedis: Jedis, record: DeviceRecord): Boolean = {
		record.sensorType match {
			//--------------------------------------------------------------
			//-- case 1: dev -> gate -> platform
			//--------------------------------------------------------------
			case "gate" =>
				var shouldSend = false
				//-- check redis-PLATFORM first and then redis-GATE
				val platform = jedis.hget(KEY_PREFIX_PLATFORM + record.getKey, PLATFORM_FIELD_NAME)

				//logger.info("----->> DoubleRedisHandler(Redis): (key, platform) = (" + record.getKey() + "," + platform + ")");
				if (platform != null) {
					val gate_rec = jedis.hget(KEY_PREFIX_GATE + record.getKey, GATE_FIELD_NAME)
					if (gate_rec == null) {
						//-- INSERT and SENT to Analyzer
						jedis.hset(KEY_PREFIX_GATE + record.getKey, GATE_FIELD_NAME, record.getValue)
						jedis.expire(KEY_PREFIX_GATE + record.getKey, RedisPool.TIME_WINDOW); //-- 5 minutes
						log.info(s"[+] actorId=[$actorIndex]: (GATE) INSERT & SEND: (K, V) = ($KEY_PREFIX_GATE${record.getKey}, ${record.getValue})")
						shouldSend = true
					}
				} else {
					val gate_rec = jedis.hget(KEY_PREFIX_GATE + record.getKey, GATE_FIELD_NAME)
					if (gate_rec == null) {
						//-- INSERT only
						jedis.hset(KEY_PREFIX_GATE + record.getKey, GATE_FIELD_NAME, record.getValue)
						jedis.expire(KEY_PREFIX_GATE + record.getKey, RedisPool.TIME_WINDOW); //-- 5 minutes
						log.info(s"[-] actorId=[$actorIndex]: (GATE) INSERT ONLY: (K, V) = ($KEY_PREFIX_GATE${record.getKey}, ${record.getValue})")
					}
				}
				shouldSend

			//--------------------------------------------------------------
			//-- case 2: dev -> platform -> gate
			//--------------------------------------------------------------
			case "platform" =>
				var shouldSend = false
				//-- check redis-GATE first and then redis-PLATFORM
				val gate = jedis.hget(KEY_PREFIX_GATE + record.getKey, GATE_FIELD_NAME)

				//logger.info("----->> DoubleRedisHandler(Redis): (key, gate) = (" + record.getKey() + "," + gate + ")" );
				if (gate != null) {
					val platform_rec = jedis.hget(KEY_PREFIX_PLATFORM + record.getKey, PLATFORM_FIELD_NAME)

					if (platform_rec == null) {
						//-- INSERT and SENT to Analyzer
						jedis.hset(KEY_PREFIX_PLATFORM + record.getKey, PLATFORM_FIELD_NAME, record.getValue)
						jedis.expire(KEY_PREFIX_PLATFORM + record.getKey, RedisPool.TIME_WINDOW); //-- 5 minutes
						log.info(s"[+] actorId=[$actorIndex]: (PLAT) INSERT & SEND: (K, V) = ($KEY_PREFIX_PLATFORM${record.getKey}, ${record.getValue})")
						shouldSend = true
					}
				} else {
					val platform_rec = jedis.hget(KEY_PREFIX_PLATFORM + record.getKey, PLATFORM_FIELD_NAME)
					if (platform_rec == null) {
						//-- INSERT only
						jedis.hset(KEY_PREFIX_PLATFORM + record.getKey, PLATFORM_FIELD_NAME, record.getValue)
						jedis.expire(KEY_PREFIX_PLATFORM + record.getKey, RedisPool.TIME_WINDOW); //-- 5 minutes
						log.info(s"[-] actorId=[$actorIndex]: (PLAT) INSERT ONLY: (K, V) = ($KEY_PREFIX_PLATFORM${record.getKey}, ${record.getValue})")
					}
				}
				shouldSend

			//--------------------------------------------------------------
			//-- case _: Nothing to do
			//--------------------------------------------------------------
			case _ =>
				false
		}
	}

//	override def process(actorIndex: Int, jedis: Jedis, record: DeviceRecord): Boolean = {
//		var shouldSendToAnalyzer = false
//
//		//--------------------------------------------------------------
//		//-- case 1: dev -> gate -> platform
//		//--------------------------------------------------------------
//		//-- 2018/8/30, gate --> T
//		if (record.sensorType == "T") {
//			//-- check redis-PLATFORM first and then redis-GATE
//			val platform = jedis.hget(KEY_PREFIX_PLATFORM + record.getKey(), PLATFORM_FIELD_NAME)
//
//			//--logger.debug("----->> DoubleRedisHandler(Redis): (key, platform) = (" + record.getKey() + "," + platform + ")");
//			if (platform != null) {
//				val gate_rec = jedis.hget(KEY_PREFIX_GATE + record.getKey(), GATE_FIELD_NAME)
//				if (gate_rec == null) {
//					//-- INSERT and SENT to Analyzer
//					jedis.hset(KEY_PREFIX_GATE + record.getKey(), GATE_FIELD_NAME, record.getValue())
//					jedis.expire(KEY_PREFIX_GATE + record.getKey(), RedisPool.TIME_WINDOW); //-- 5 minutes
//					//--log.debug(s"[+] actorId=[$actorIndex]: (GATE) INSERT & SEND: (K, V) = ($KEY_PREFIX_GATE${record.getKey()}, ${record.getValue()})")
//					shouldSendToAnalyzer = true
//				}
//			} else {
//				val gate_rec = jedis.hget(KEY_PREFIX_GATE + record.getKey(), GATE_FIELD_NAME)
//				if (gate_rec == null) {
//					//-- INSERT only
//					jedis.hset(KEY_PREFIX_GATE + record.getKey(), GATE_FIELD_NAME, record.getValue())
//					jedis.expire(KEY_PREFIX_GATE + record.getKey(), RedisPool.TIME_WINDOW); //-- 5 minutes
//					//--log.debug(s"[-] actorId=[$actorIndex]: (GATE) INSERT ONLY: (K, V) = ($KEY_PREFIX_GATE${record.getKey()}, ${record.getValue()})")
//				}
//			}
//
//			return shouldSendToAnalyzer
//		}
//
//
//		//--------------------------------------------------------------
//		//-- case 2: dev -> platform -> gate
//		//--------------------------------------------------------------
//		//-- 2018/8/30, platform --> P
//		if (record.sensorType == "P") {
//			//-- check redis-GATE first and then redis-PLATFORM
//			val gate = jedis.hget(KEY_PREFIX_GATE + record.getKey(), GATE_FIELD_NAME)
//
//			//logger.debug("----->> DoubleRedisHandler(Redis): (key, gate) = (" + record.getKey() + "," + gate + ")" );
//			if (gate != null) {
//				val platform_rec = jedis.hget(KEY_PREFIX_PLATFORM + record.getKey(), PLATFORM_FIELD_NAME)
//
//				if (platform_rec == null) {
//					//-- INSERT and SENT to Analyzer
//					jedis.hset(KEY_PREFIX_PLATFORM + record.getKey(), PLATFORM_FIELD_NAME, record.getValue())
//					jedis.expire(KEY_PREFIX_PLATFORM + record.getKey(), RedisPool.TIME_WINDOW); //-- 5 minutes
//					//--log.debug(s"[+] actorId=[$actorIndex]: (PLAT) INSERT & SEND: (K, V) = ($KEY_PREFIX_PLATFORM${record.getKey()}, ${record.getValue()})")
//
//					shouldSendToAnalyzer = true
//				}
//			} else {
//				val platform_rec = jedis.hget(PLATFORM_FIELD_NAME, record.getKey())
//				if (platform_rec == null) {
//					//-- INSERT only
//					jedis.hset(KEY_PREFIX_PLATFORM + record.getKey(), PLATFORM_FIELD_NAME, record.getValue())
//					jedis.expire(KEY_PREFIX_PLATFORM + record.getKey(), RedisPool.TIME_WINDOW); //-- 5 minutes
//					//--log.debug(s"[-] actorId=[$actorIndex]: (PLAT) INSERT ONLY: (K, V) = ($KEY_PREFIX_PLATFORM${record.getKey()}, ${record.getValue()})")
//				}
//			}
//		}
//
//		shouldSendToAnalyzer
//	}

}
