package com.iheart.sqs


import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import org.mapdb.{DBMaker, Serializer}
import org.uaparser.scala.CachingParser
import play.Logger

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object DBUtils {

  val format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
  val dbFile = DBMaker.memoryDB().make()
  val dbHash = dbFile.hashMap("msgMap").keySerializer(Serializer.STRING).valueSerializer(Serializer.LONG).createOrOpen()
  val zoneId = ZoneId.of("America/New_York")


  def storeHostname(hostname: String) = {
    val now = LocalDateTime.now().atZone(zoneId).toEpochSecond
    dbHash.put(hostname,now)
  }

  def startTimer() = {
    Logger.debug("Starting timer")
    Future {
      while(true) {
        Logger.info("****************************")
        dbHash.getKeys.asScala.foreach{ key =>
          val date = new Date(dbHash.get(key) * 1000)
          Logger.info(key + " -> " + date.toString)
        }
        Logger.info("****************************")
        Thread.sleep(30000)
      }
    }
  }

}
