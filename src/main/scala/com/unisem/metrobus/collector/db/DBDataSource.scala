/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.db

import java.sql.{Connection, SQLException}

import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}


object DBDataSource {
	private val appConf = ConfigFactory.load()
	private val JDBConnInfo = appConf.getString("mariaDB.db.url")
	private val dataSourceClassName = appConf.getString("mariaDB.db.dataSourceClass")
	private val initialSize = 5
	private val maxPoolSize = 20
	private val userId = appConf.getString("mariaDB.db.user")
	private val password = appConf.getString("mariaDB.db.password")

	private val config = new HikariConfig
	private var ds: HikariDataSource = null


	//#---------------------------------------
	//# DB config
	//#----------------
	def apply() = {
		config.setPoolName("Collector-DB-Pool")
		config.setJdbcUrl(JDBConnInfo)
		config.setDataSourceClassName(dataSourceClassName)
		config.setMinimumIdle(initialSize)
		config.setMaximumPoolSize(maxPoolSize)
		config.addDataSourceProperty("url", JDBConnInfo)
		config.addDataSourceProperty("user", userId)
		config.addDataSourceProperty("password", password)

		ds = new HikariDataSource(config)
	}


	@throws[SQLException]
	def getConnection(ThreadIdx: Int): Connection = {
		if (ds == null) {
			config.setPoolName("Collector-DB-Pool")
			config.setJdbcUrl(JDBConnInfo)
			config.setDataSourceClassName(dataSourceClassName)
			config.setMinimumIdle(initialSize)
			config.setMaximumPoolSize(maxPoolSize)
			config.addDataSourceProperty("url", JDBConnInfo)
			config.addDataSourceProperty("user", userId)
			config.addDataSourceProperty("password", password)

			ds = new HikariDataSource(config)
		}
		ds.getConnection
	}

	def close(): Unit = {
		if (ds != null && !ds.isClosed) {
			ds.close()
		}
	}

}
