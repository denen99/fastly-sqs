package com.iheart.sqs

import java.util.concurrent.Executors

import com.iheart.sqs.Utils._
import play.Logger
import play.api.libs.ws.ning.NingWSClient
import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration.Duration

object NewRelic {

  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
  val wsClient = NingWSClient()


  /**********************************************
    * make API call to NewRelic
    *
    * splitCount splits up the JSON posts so we
    * stay below the 5MB NewRelic limit
    **********************************************/
  def postJson(entries: Iterator[LogEntry]) = {
    val json = entries.toSeq.asJ
    wsClient.url(insightUrl)
      .withHeaders(("X-Insert-Key", insightApiKey), ("Content-Type", "application/json"))
      .withRequestTimeout(2000)
      .post(json)
  }

  def sendToNewRelicChunk(entries: Iterator[LogEntry], splitCount: Int ): Unit = entries.hasNext match {
    case true =>
      Logger.debug("Sending chunk to NewRelic")
      postJson(entries.take(splitCount))
      sendToNewRelicChunk(entries.drop(splitCount),splitCount)
    case _ =>
      Logger.debug("Done with NewRelic Chunks")
  }

  def sendToNewRelic(s3Helper: S3TupleBase, splitCount: Int = 2000) = {

    val validEntries = s3Helper.iterator.flatMap(y => y)

    validEntries.isEmpty match {
      case true =>
        Logger.debug("Skipping NewRelic, no valid entries")
      case _ =>
        sendToNewRelicChunk(validEntries,splitCount)
        Logger.debug("Closing S3 handle")
        s3Helper.handle.close()
    }

  }
}
