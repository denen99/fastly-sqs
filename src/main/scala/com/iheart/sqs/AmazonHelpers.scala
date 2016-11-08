package com.iheart.sqs

import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.services.logs.AWSLogsClient
import com.amazonaws.services.logs.model._
import com.amazonaws.services.s3.{AmazonS3Client, S3ResponseMetadata}
import com.amazonaws.services.s3.model.{GetObjectRequest, S3Object}
import com.amazonaws.services.sqs.{AmazonSQSAsyncClient, AmazonSQSClient}
import com.amazonaws.services.sqs.buffered.AmazonSQSBufferedAsyncClient
import com.amazonaws.services.sqs.model._
import com.iheart.sqs.NewRelic._
import com.iheart.sqs.Utils._
import org.apache.http.conn.ConnectionPoolTimeoutException
import play.Logger
import play.api.libs.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Random


object AmazonHelpers {

  val s3Client = new AmazonS3Client()
  val cwlClient = new AWSLogsClient()
  val sqsAsync = new AmazonSQSAsyncClient();
  val sqsclient = new AmazonSQSBufferedAsyncClient(sqsAsync)
  val cwlLogGroup = conf.getString("sqs.logGroup")
  val sqsQueueUrl = conf.getString("sqs.url")
  implicit val ec = executionContext

  def cwlLogStream() = {
    val s = new SimpleDateFormat("YYYY-MMDD-HH-mm")
    s.format(new Date())
  }

  def readFileFromS3(bucket: String, key: String): Either[Throwable, List[String]] = {
    Logger.debug("About to read from bucket : " + bucket + " and key " + key)
    var source: Source = null
    var meta: S3ResponseMetadata = null

    try {
      val s3Req = new GetObjectRequest(bucket, key)
      meta = s3Client.getCachedResponseMetadata(s3Req)
      val s3Object = s3Client.getObject(s3Req)
      source = Source.fromInputStream(s3Object.getObjectContent)(scala.io.Codec.ISO8859)
      val lines = source.getLines().toList
      Right(lines)
    } catch {
      case e: ConnectionPoolTimeoutException =>
        Logger.error("Error retrieving from connection pool, possibly threadPool issue?")
        Left(e)
      case e: Throwable =>
        Logger.error("Error retrieving from S3 for Request ID " + meta.getRequestId + " : " +  e.getMessage)
        Left(e)
    } finally {
      if (source != null) source.close()
    }
  }

  def getCloudSeqToken(logStream: String) = {
    val req = new DescribeLogStreamsRequest(cwlLogGroup).withLogStreamNamePrefix(logStream)
    val descResult = cwlClient.describeLogStreams(req)
    if (descResult != null && descResult.getLogStreams != null && !descResult.getLogStreams.isEmpty) {
      descResult.getLogStreams.asScala.last.getUploadSequenceToken
    }
    else {
      cwlClient.createLogStream(new CreateLogStreamRequest(cwlLogGroup,logStream))
      null
    }
  }

  def sendCloudWatchLog(log: String) = {
    val logStream = cwlLogStream()
    val token = getCloudSeqToken(logStream)
    val event = new InputLogEvent
    event.setTimestamp(new Date().getTime)
    event.setMessage(log)
    val req = new PutLogEventsRequest(cwlLogGroup,logStream,List(event).asJava)
    req.setSequenceToken(token)
    cwlClient.putLogEvents(req)
  }

  // Return a tuple of (bucket,key) from the JSON
  def parseMessage(body: String): Seq[(String,String)] = {
    val json = Json.parse(body)
    val records = (json  \ "Records").as[Seq[JsValue]]

    val bucketTransform = ( __ \ "s3" \ "bucket" \ "name").json.pick
    val keyTransform = (__ \ "s3" \ "object" \ "key").json.pick

    records.map { record =>
      (record.transform(bucketTransform).asOpt,record.transform(keyTransform).asOpt)
    }.filter(x => x._1.isDefined && x._2.isDefined).map {
      case (x,y) => (x.get.as[String],y.get.as[String])
    }

  }

  def deleteBatch(messages: Seq[Message]) = {
    Logger.debug("About to delete " + messages.size + " messages")
    val batchReq = messages.map(m =>
      new DeleteMessageBatchRequestEntry(Random.alphanumeric.take(20).mkString,m.getReceiptHandle))
    val delBatchRequest = new DeleteMessageBatchRequest(sqsQueueUrl,batchReq.asJava)
    sqsclient.deleteMessageBatchAsync(delBatchRequest)
  }

  def getSqsMessages(implicit ec: ExecutionContext = executionContext) = {
    while (true) {
      try {
        val request = new ReceiveMessageRequest(sqsQueueUrl).withVisibilityTimeout(30).withMaxNumberOfMessages(10).withWaitTimeSeconds(10)
        val messages = sqsclient.receiveMessage(request).getMessages
        Logger.debug("Received " + messages.size() + " messages")

        messages.asScala.foreach { message =>
          val body = message.getBody
          parseMessage(body).map { record =>
            val bucket = record._1
            val key = URLDecoder.decode(record._2, "UTF-8")
            Logger.debug("Sending bucket : " + bucket + " and key:" + key + ":")
            Future {
              sendToNewRelic(parseLogFile(bucket, key))
            }
          }
        }

        deleteBatch(messages.asScala)
      } catch {
        case e: Exception => Logger.error("Error in SQS Poll, " + e.getMessage)
      }

    }
  }
}