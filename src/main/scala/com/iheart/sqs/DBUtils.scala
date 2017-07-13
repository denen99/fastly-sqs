package com.iheart.sqs


import java.text.SimpleDateFormat
import java.time.{LocalDateTime, ZoneId}
import java.util.Date

import scala.concurrent.duration._
import com.iheart.sqs.Utils._
import org.mapdb.{DBMaker, Serializer}
import play.Logger

import scala.collection.JavaConverters._
import java.util.concurrent.Executors

import scala.concurrent._
import java.util.concurrent.atomic._

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.Serialization.write
import play.api.libs.ws.ning.NingWSClient


object DBUtils {

  implicit val formats = DefaultFormats

  val format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
  val dbFile = DBMaker.memoryDB().make()
  val dbHash = dbFile.hashMap("msgMap").keySerializer(Serializer.STRING).valueSerializer(Serializer.LONG).createOrOpen()
  val zoneId = ZoneId.of("America/New_York")
  val ddApiKey = conf.getString("datadog.apiKey")
  val ddAppKey = conf.getString("datadog.appKey")
  val ddHostname = java.net.InetAddress.getLocalHost.getHostName
  val ddHostTag = "hostname:" + ddHostname
  val ddUrl = "https://app.datadoghq.com/api/v1/series?api_key=" + ddApiKey

  //Lets create our own thread pool
  val numWorkers = sys.runtime.availableProcessors
  val pool = Executors.newFixedThreadPool(numWorkers)
  implicit val ec = ExecutionContext.fromExecutorService(pool)

  val wsClient = NingWSClient()



  private val s3Counter = new AtomicInteger()
  private val newRelicCounter = new AtomicInteger()

  case class DDEntry(metric: String, points: Seq[(Long,Int)], metricType: String, tags: Seq[String])

  def incrS3Counter = {
    s3Counter.incrementAndGet()
  }

  def decrS3Counter = {
    s3Counter.decrementAndGet()
  }

  def incrNewRelicCounter(i: Int) =
    newRelicCounter.addAndGet(i)

  def storeHostname(hostname: String) = {
    val now = LocalDateTime.now().atZone(zoneId).toEpochSecond
    dbHash.put(hostname,now)
  }

  private def submitToDD(metrics: Seq[DDEntry]) = {
    val json = "series" -> metrics.map { m =>
      ("metric" -> m.metric) ~
      ("type" -> m.metricType) ~
      ("tags" -> m.tags) ~
      ("points" -> m.points.map(tuple => Seq(JInt(tuple._1), JDouble(tuple._2))))
    }

    Logger.debug("Sending json : " + write(json))

    wsClient.url(ddUrl)
      .withHeaders(("Content-Type", "application/json"))
      .withRequestTimeout(2000)
      .post(write(json)).map { response =>
      if (response.status >= 400 ) {
        Logger.error("Invalid Status Code from DataDog: " + response.status.toString + " Error: " + response.body)
      }
    }.andThen { case _ => wsClient.close() }

  }

  def startDataDogTimer() = {
    Logger.info("Starting datadog timer...")
    Future {
      blocking {
        while (true) {
          val nrCount = newRelicCounter.getAndSet(0)
          val s3Count = s3Counter.getAndSet(0)
          val threads =  Thread.activeCount()
          val now = LocalDateTime.now().atZone(zoneId).toEpochSecond
          //sendToDatadog
          val m1 = DDEntry("fastlyinsights.s3Count",Seq((now,s3Count)),"guage",Seq(ddHostTag))
          val m2 = DDEntry("fastlyinsights.newRelicCount",Seq((now,nrCount)),"guage",Seq(ddHostTag))
          val m3 = DDEntry("fastlyinsights.jvmThreadCount",Seq((now,threads)),"guage",Seq(ddHostTag))

          submitToDD(Seq(m1,m2))
          Thread.sleep(10000)
        }
      }
    }
  }

  def startTimer() = {
    Logger.info("Starting DB timer")
    Future {
      blocking {
        while (true) {
          Logger.info("****************************")
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
