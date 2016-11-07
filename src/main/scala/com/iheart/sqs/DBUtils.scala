package com.iheart.sqs


import java.text.SimpleDateFormat
import java.util.Date

import org.mapdb.{DBMaker, Serializer}
import play.Logger

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object DBUtils {

  val format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
  val dbFile = DBMaker.memoryDB().make()
  val dbHash = dbFile.hashMap("msgMap").keySerializer(Serializer.STRING).valueSerializer(Serializer.LONG).createOrOpen()

  def storeHostname(hostname: String, timestamp: Long) = {
    dbHash.put(hostname,timestamp)
  }

  def startTimer() = {
    Logger.debug("Starting timer")
    Future {
      while(true) {
        Logger.debug("****************************")
        dbHash.getKeys.asScala.foreach{ key =>
          val date = new Date(dbHash.get(key) * 1000)
          Logger.debug(key + " -> " + date.toString)
        }
        Logger.debug("****************************")
        Thread.sleep(10000)
      }
    }
  }

}
