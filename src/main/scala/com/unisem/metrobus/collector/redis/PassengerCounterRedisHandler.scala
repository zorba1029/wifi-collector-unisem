package com.unisem.metrobus.collector.redis

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.unisem.metrobus.collector.messages.Messages
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

object PassengerCounterRedisHandler {
	val RETENTION_TIME_WINDOW = 10  //-- 10 seconds
	val PASSENGER_COUNTER_FIELD_NAME = "passenger_field"
	val KEY_PREFIX = "p_count##"
	val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}


class PassengerCounterRedisHandler extends RedisHandler {
	import PassengerCounterRedisHandler._
	val log = LoggerFactory.getLogger(classOf[PassengerCounterRedisHandler])


	override def process(actorIndex: Int, jedis: Jedis, record: Messages.DeviceRecord): Boolean = {
		var shouldSendToAggregator = false

		val platform = jedis.hget(KEY_PREFIX + record.getKey, PASSENGER_COUNTER_FIELD_NAME)

		if (platform != null) {
			//-- ALREADY EXIST
			//--log.debug(s"[+] actorId=[$actorIndex]: -------- DISCARD: SensorID: (${record.sensorID}, $KEY_PREFIX${record.getKey()}), newDT = ${record.getValue()}, oldDT = ${platform}")
			shouldSendToAggregator = false

		} else {
			//-- NEW
			jedis.hset(KEY_PREFIX + record.getKey, PASSENGER_COUNTER_FIELD_NAME, record.getValue)
			jedis.expire(KEY_PREFIX + record.getKey, RETENTION_TIME_WINDOW)

			//--log.debug(s"[+] actorId=[$actorIndex]: --------  NEW: SensorID: ${record.sensorID}, (K, V) = ($KEY_PREFIX${record.getKey()}, ${record.getValue()})")
			shouldSendToAggregator = true
		}

		shouldSendToAggregator
	}

}
