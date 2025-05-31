#!/usr/bin/env bash
#java -Dconfig.file=myconf.conf -cp ./secure-wifi-server-as.jar com.unisem.wifiServer.receiver.WifiReceiverMain

#java -Dconfig.file=myconf.conf -cp ./secure-wifi-server-as-slick.jar com.unisem.wifiServer.receiver.WifiReceiverMain

java -Dconfig.file=collector.conf -cp ./metrobus-collector-actor.jar com.unisem.metrobus.collector.MetrobusCollectorMain
