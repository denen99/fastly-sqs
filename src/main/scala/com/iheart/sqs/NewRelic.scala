package com.iheart.sqs

import com.iheart.sqs.Utils._
import play.Logger
import play.api.libs.ws.ning.NingWSClient
import scala.concurrent.ExecutionContext.Implicits.global

object NewRelic {

  val wsClient = NingWSClient()


  /**********************************************
    * make API call to NewRelic
    *
    * splitCount splits up the JSON posts so we
    * stay below the 5MB NewRelic limit
    **********************************************/
  def postJson(entries: List[LogEntry]) = {
    DBUtils.incrNewRelicCounter(entries.size)
    val json = entries.asJ
    wsClient.url(insightUrl)
      .withHeaders(("X-Insert-Key", insightApiKey), ("Content-Type", "application/json"))
      .withRequestTimeout(2000)
      .post(json).map { response =>
        if (response.status != 200 ) {
          Logger.error("Invalid Status Code " + response.status.toString + " Error: " + response.body)
        }
      }.andThen { case _ => wsClient.close() }
  }

  def sendToNewRelicChunk(entries: List[LogEntry], splitCount: Int): Unit = entries.nonEmpty match {
    case true =>
      postJson(entries.take(splitCount))
      sendToNewRelicChunk(entries.drop(splitCount), splitCount)
    case _ =>
      Logger.debug("Done with NewRelic Chunks")
  }

  def sendToNewRelic(entries: List[LogEntry], splitCount: Int = 2000) = {
    entries.isEmpty match {
      case true =>
        Logger.debug("Skipping NewRelic, no valid entries")
      case _ =>
        Logger.debug("Sending Chunks to NewRelic")
        sendToNewRelicChunk(entries, splitCount)
    }
  }
}
