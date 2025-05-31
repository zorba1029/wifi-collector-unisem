#!/usr/bin/env bash
nohup java -Dconfig.file=collector.conf -cp ./metrobus-collector-actor.jar com.unisem.metrobus.collector.MetrobusCollectorMain > /dev/null 2>&1 &
#nohup java -Dconfig.file=myconf.conf -cp ./secure-wifi-server-as-slick.jar com.unisem.wifiServer.receiver.WifiReceiverMain > /dev/null 2>&1 &