/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.redis

import com.unisem.metrobus.collector.messages.Messages.DeviceRecord
import redis.clients.jedis.Jedis

trait RedisHandler {
	def process(actorIndex: Int, jedis: Jedis, record: DeviceRecord): Boolean
}