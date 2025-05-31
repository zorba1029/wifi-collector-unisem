/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.redis

import com.typesafe.config.ConfigFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object RedisPool {
	val TIME_WINDOW = 300  //-- (seconds) 5 minutes
	var pool: JedisPool = _
	var poolDoubleRedis: JedisPool = _
	var poolPassengerCountRedis: JedisPool = _
	val appConfig = ConfigFactory.load()
	val mRedisServerIP = appConfig.getString("collector.redis_server.redis_server_ip")

	//	def apply() {
	//		poolDoubleRedis = new JedisPool(new JedisPoolConfig(), mRedisServerIP)
	//	}

	def getPool(): JedisPool = {
		if (pool == null) {
			pool = new JedisPool(new JedisPoolConfig(), mRedisServerIP)
		}
		pool
	}

	def getDoubleRedis(): JedisPool = {
		if (poolDoubleRedis == null) {
			poolDoubleRedis = new JedisPool(new JedisPoolConfig(), mRedisServerIP)
		}
		poolDoubleRedis
	}

	def getPassengerCountRedis(): JedisPool = {
		if (poolPassengerCountRedis == null) {
			poolPassengerCountRedis = new JedisPool(new JedisPoolConfig(), mRedisServerIP)
		}
		poolPassengerCountRedis
	}
}

