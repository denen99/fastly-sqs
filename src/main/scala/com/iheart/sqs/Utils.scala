package com.iheart.sqs

import java.text.SimpleDateFormat
import java.util.regex.{Matcher, Pattern}
import scala.collection.JavaConverters._
import org.json4s.{DefaultFormats, FieldSerializer}
import org.json4s.native.Serialization.write
import com.typesafe.config._
import play.Logger
import scala.concurrent._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.uaparser.scala.CachingParser

case class LogEntry(fields: Map[String,Any])

case class LogConfig(pattern: String, captures: Map[String,String], dateformat: String)

object Utils  {

  type PatternMap = Map[String,Pattern]
  type ConfMap = Map[String,LogConfig]

  import com.iheart.sqs.AmazonHelpers._

  //implicit Class to convert case class to JSON
  implicit class logEntryToJson(l: Seq[LogEntry]) {
    implicit val formats = DefaultFormats + FieldSerializer[LogEntry]()

    def asJ = write(l.map(_.fields))
  }

  type EmptyResponse = String

  val conf = ConfigFactory.load()
  val insightApiKey = conf.getString("newrelic.apikey")
  val insightUrl = conf.getString("newrelic.apiUrl")
  val integerFields: Seq[String] = conf.getStringList("regex.integerFields").asScala.toSeq
  val floatFields: Seq[String] = conf.getStringList("regex.floatFields").asScala.toSeq
//  val executorService = Executors.newFixedThreadPool(16)
//  val executionContext = ExecutionContext.fromExecutorService(executorService)

  val default: LogConfig = conf.as[LogConfig]("regex.default")
  val defaultPattern = Pattern.compile(default.pattern)
  val confMap: ConfMap = buildConfMap()
  val patternMap: Map[String, Pattern] = buildPatternMap().withDefaultValue(defaultPattern)
  val uaParser = CachingParser.get(100000)

  val eventTypes = buildEventTypes()


  def buildEventTypes() = {
     conf.getObject("event-types")
  }
  def buildConfMap(): ConfMap =
    conf.as[ConfMap]("regex.hosts").withDefaultValue(default)

  def buildPatternMap(): PatternMap = {

    def buildPatternMapRec(keys: List[String], m: PatternMap = Map()): PatternMap = keys match {
      case h :: t => buildPatternMapRec(t, m + (h -> Pattern.compile(confMap(h).pattern)))
      case Nil => m
    }

    buildPatternMapRec(confMap.keys.toList)
  }

  def getHostFromKey(key: String) = {
    val arr = key.split("/")
    arr(arr.size - 2)
  }

  /******************************************
    * parses application.conf for the eventType
    * to send to NewRelic using hostname
    ******************************************** */
  def getEventType(hostname: String) = {
    val key = "event-types." + hostname
    conf.hasPath(key) match {
      case true => conf.getString(key)
      case false => conf.getString("event-types.default")
    }
  }

  /****************************************
    * Reads a file from S3, parses it and returns
    * a sequence of Option[LogEntry]
    **********************************************/
  def parseLogFile(bucket: String, key: String): List[LogEntry] = {
    try {
      blocking {
        readFileFromS3(bucket, key) match {
          case Right(lines) =>
            DBUtils.storeHostname(getHostFromKey(key))
            lines.flatMap(line => parseRecord(line, getHostFromKey(key)))
          case Left(y) => Nil
        }
      }
    } catch {
      case e: Throwable =>
        Logger.debug("Unable to parse Logfile " + e.getMessage)
        Nil
    }

  }


  /******************************************
    * Date format helper to convert date in log
    * to EPOCH format
  **********************************************/
  def parseDate(date: String, host: String): Long = {
    val fmt = new SimpleDateFormat(confMap(host).dateformat)
    val res = fmt.parse(date)
    res.getTime / 1000
  }

  /*************************************************
    * Convert parsed UserAgent into a string we can use
  ******************************************************/
  def parseUserAgent(ua: String) = {
     val client = uaParser.parse(ua)
     client.os.family + ' ' + client.userAgent.family
  }

  /*****************************************************************
    * 2 keys in the map are special, hostname and timestamp.  NewRelic
    * uses the timestamp field in the JSON as the date they store.  The
    * hostname field is used to map to a custom eventType field. The
    * eventType field is how NewRelic stores different events inside
    * of Insights.
  ********************************************************************/
  def formatValue(key: String, value: Any, host: String): Map[String,Any] = key match {
    case "timestamp" => Map(key -> parseDate(value.asInstanceOf[String],host))
    //case "hostname" => Map(key -> value, "eventType" -> getEventType(key))
    case "userAgent" => Map(key -> parseUserAgent(value.asInstanceOf[String]))
    case _ if integerFields.contains(key) => Map(key -> value.asInstanceOf[String].toInt )
    case _ if floatFields.contains(key) => Map(key -> value.asInstanceOf[String].toFloat)
    case _ => Map(key -> value)
  }

  /*****************************************************
    * NewRelic requires a field called eventType ,
    * so we ensure its there
  ******************************************************/
  def ensureEventType(hostname: String, m: Map[String,Any]) = m.get("eventType") match {
    case None => Map("eventType" -> getEventType(hostname))
    case _ => Map()
  }

  /**************************************************
    * compiles Regex against log entry to build a
    * a map used to create a LogEntry class
  **************************************************/

  def buildMap(matcher: Matcher,count: Int, host: String, m: Map[String,Any] = Map()): Map[String,Any] = count match {
    case 0 => m ++ ensureEventType(host,m)
    case _ => val key = confMap(host).captures.get(count.toString)
      key match {
        case Some(x) =>
          buildMap(matcher,count-1, host, m ++ formatValue(x,matcher.group(count),host))
        case None => //no regex.COUNT in application.conf
          buildMap(matcher,count-1,host, m)
      }
  }

  /**********************************************************
    * This is the method that gets passed an entry from
    * the logfile, parses it and returns an Option[LogEntry]
    ************************************************************/
  def parseRecord(line: String, host: String): Option[LogEntry] = {

    val pattern = patternMap(host)
    val matcher = pattern.matcher(line)

    if (matcher.find()) {
      Some(LogEntry(buildMap(matcher, matcher.groupCount(),host)))
    }
    else {
      //Logger.debug("No match for line " + line)
      //sendCloudWatchLog(line)
      None
    }

  }
}