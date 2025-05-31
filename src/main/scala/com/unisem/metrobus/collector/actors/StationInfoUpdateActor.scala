/**
	* Copyright (C) 2017 Unisem Inc. <http://www.unisem.co.kr>
	*
	* Created by Heung-Mook CHOI (zorba)
	*/
package com.unisem.metrobus.collector.actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable}
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.unisem.metrobus.collector.messages.Messages

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}


class StationInfoUpdateActor extends Actor with ActorLogging {
	import io.circe.parser._
	implicit val system: ActorSystem = context.system
	implicit val materializer: ActorMaterializer = ActorMaterializer()
	implicit val executor: ExecutionContextExecutor = context.dispatcher
	implicit val timeout: Timeout = Timeout(10 seconds)

	val appConfig = ConfigFactory.load()
	val api_service_ip_port = appConfig.getString("collector.api_service.server_ip_port")
	val station_layout_info_url = appConfig.getString("collector.api_service.station_layout_info_url")

	var stationInfoScheduler: Cancellable = _
	var stationIdNameScheduler: Cancellable = _

	//-- [sensor_id, (sensor_type, station_id)]
	var stationInfoMap = scala.collection.immutable.Map.empty[String, (String,String)]

	val stationInfoRequest: HttpRequest = HttpRequest(uri = s"http://${api_service_ip_port}/${station_layout_info_url}",
																				entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, ""))
	//-- 2018/8/30, ADDED
	var stationNameListMap = scala.collection.immutable.Map.empty[String, String]

	val stationNameListRequest: HttpRequest = HttpRequest(uri = s"http://${api_service_ip_port}/StationIdNameList",
																				entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, ""))

	//	val stationInfoRequest = HttpRequest(HttpMethods.GET,
	//																			uri = s"http://${api_service_ip_port}/${station_layout_info_url}" ,
	//																			entity = HttpEntity(ContentTypes.`application/json`, "")
	//																		)
	//  --.withHeaders(RawHeader("X-Access-Token", "access token"))
	val httpClient: HttpExt = Http(context.system)


	override def preStart = {
		log.debug(s"[*] ~~>> preStart() ")

		self ! Messages.UpdateStationInfo
		stationInfoScheduler = context.system.scheduler.schedule(10 seconds, 10 minutes, self, Messages.UpdateStationInfo)

		self ! Messages.UpdateStationIdNameList
		stationIdNameScheduler = context.system.scheduler.schedule(10 seconds, 10 minutes, self, Messages.UpdateStationIdNameList)
	}

	override def postStop = {
		stationInfoScheduler.cancel()
		stationIdNameScheduler.cancel()
		log.debug(s"*] ~~>> postStop() - DONE")
	}

	override def receive: Receive = {
		case Messages.UpdateStationInfo =>
			log.debug(s"[*] Request - UpdateStationInfo -----------------")
			val responseFuture = Source(0 to 0).mapAsync(1) { r =>
					//--log.debug(s"count = $r")
					httpClient.singleRequest(stationInfoRequest)
				}
				.mapAsync(1) { r => Unmarshal(r.entity).to[String] }
				.map { data => {
					//--log.debug(s"StationLayoutInfo LIST = $data")
					//-- 2018/5/29, used circe Json decoder
					decode[List[Map[String,String]]](data) match {
						case Right(list) =>
							stationInfoMap = Map(list map { item =>
								val sensorId = item("sensorID")
								val sensorType = item("sensorType")
								val stationId = item("stationID")
								sensorId -> (sensorType, stationId)
							}: _*)
							//	val tmp = list map { item =>
							//		val sensorId = item("sensorID")
							//		val sensorType = item("sensorType")
							//		val stationId = item("stationID")
							//		sensorId -> (sensorType, stationId)
							//	}
							//	stationInfoMap = tmp.toMap
							log.debug(s"[*]  GOT Response - UPDATE(StationLayoutInfoMap) = ${stationInfoMap}")
						case Left(parsingFailure) =>
							log.warning(s"[*]  GOT Response - UPDATE(StationLayoutInfoMap) = $parsingFailure")
					}
				}}
				.runWith(Sink.ignore)

			val resResult = Await.ready(responseFuture, 30 seconds)
			resResult onComplete {
				case Success(res) =>
					log.debug(s"[*] - Success(res): Http-Client Request: $res ----------------")
				case Failure(failure) =>
					log.warning(s"[*] - Failure(_): - Http-Client Request is FAILED (Please, check Api-Server is running): $failure")
			}

		case Messages.RequestSensorStationInfo =>
			sender ! Messages.ResponseSensorStationInfo(stationInfoMap)

		//-- 2018/8/30, ADDED ----------------------------------
		case Messages.UpdateStationIdNameList =>
			log.debug(s"[*] Request - UpdateStationIdNameListInfo -----------------")
			val responseFuture = Source(0 to 0).mapAsync(1) { r =>
				//--log.debug(s"count = $r")
				httpClient.singleRequest(stationNameListRequest)
			}
			.mapAsync(1) { r => Unmarshal(r.entity).to[String] }
			.map { data => {
				//--log.debug(s"StationNameList = $data")
				//-- 2018/5/29, used circe Json decoder
				decode[List[Map[String,String]]](data) match {
					case Right(list) =>
						stationNameListMap = Map(list map { item =>
							val stationId = item("stationID")
							val stationName = item("stationName")
							stationId -> stationName
						}: _*)
						log.debug(s"[*]  GOT Response - UPDATE(UpdateStationIdNameListInfo) = ${stationNameListMap}")
					case Left(parsingFailure) =>
						log.warning(s"[*]  ERROR - GOT Response - UPDATE(UpdateStationIdNameListInfo) = $parsingFailure")
				}
			}}
			.runWith(Sink.ignore)

			val resResult = Await.ready(responseFuture, 30 seconds)
			resResult onComplete {
				case Success(res) =>
					log.debug(s"[*] - Success(res): Http-Client Request: $res ----------------")
				case Failure(failure) =>
					log.error(s"[*] - Failure(_): - Http-Client Request is FAILED (Please, check Api-Server is running): $failure")
			}

		case Messages.RequestStationIdNameList =>
			sender() ! Messages.ResponseStationIdNameList(stationNameListMap)

		case _ =>
			log.debug(s"[$$] --- un-handled message ==")
	}
}
