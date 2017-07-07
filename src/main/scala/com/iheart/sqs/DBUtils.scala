package com.iheart.sqs


import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import org.mapdb.{DBMaker, Serializer}
import play.Logger

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import java.util.concurrent.atomic._



object DBUtils {

  val format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
  val dbFile = DBMaker.memoryDB().make()
  val dbHash = dbFile.hashMap("msgMap").keySerializer(Serializer.STRING).valueSerializer(Serializer.LONG).createOrOpen()
  val zoneId = ZoneId.of("America/New_York")
  var counter = new AtomicInteger()

  def incrS3Counter = {
    counter.incrementAndGet()
  }

  def decrS3Counter = {
    counter.decrementAndGet()
  }

  def storeHostname(hostname: String) = {
    val now = LocalDateTime.now().atZone(zoneId).toEpochSecond
    dbHash.put(hostname,now)
  }

  def startTimer() = {
    Logger.debug("Starting timer")
    Future {
      blocking {
        while (true) {
          val count = Thread.activeCount()
          val downloads = counter.get()
          Logger.info("****************************")
          Logger.info("# Running Threads: " + count)
          Logger.info("# Active Logs: " + downloads)
          dbHash.getKeys.asScala.foreach { key =>
            val date = new Date(dbHash.get(key) * 1000)
            Logger.info(key + " -> " + date.toString)
          }
          Logger.info("****************************")
          Thread.sleep(30000)
        }
      }
    }
  }

}
