package com.iheart.lambda

import com.typesafe.config.{ConfigFactory, Config}
import org.specs2.mutable._
import com.iheart.sqs.Utils._
import play.Logger
import scala.collection.JavaConversions._

class LambdaSpec extends Specification {


  "Lambda Application" should {



    "correctly parse a valid log entry" in {
      val record = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS (null) 25000"
      val host = "random.example.com"

      val result = parseRecord(record,host)
      result mustNotEqual None
      result.get.fields("hostname") mustEqual "www.domain.com"
      result.get.fields("hitMissShield") mustEqual "HIT"
      result.get.fields("hitMissEdge") mustEqual "MISS"
      result.get.fields("httpMethod") mustEqual "GET"
      result.get.fields("eventType") mustEqual "FastlyDebug"
      result.get.fields("ip") mustEqual "1.2.3.4"
      result.get.fields("statusCode") mustEqual "200"
      result.get.fields("uri") mustEqual "/path/hello.txt"
      result.get.fields("timestamp") mustEqual 1452278159
      result.get.fields("fastlyHost") mustEqual "cache-atl6234"
      result.get.fields("referrer") mustEqual "(null)"
    }

    "correctly parse a valid log entry with multiple shield entries" in {
      val record ="<134>2016-02-22T00:00:09Z cache-ord1732 AmazonS3[351]: 205.160.165.83 Mon, 22 Feb 2016 00:00:08 GMT GET /path/hello.txt radioedit.example.com 200 MISS, MISS, HIT (null) 25000"
      val host = "random.example.com"

      val result = parseRecord(record,host)
      result mustNotEqual None
      result.get.fields("hostname") mustEqual "radioedit.example.com"
      result.get.fields("hitMissShield") mustEqual "MISS"
      result.get.fields("hitMissEdge") mustEqual "MISS"
      result.get.fields("httpMethod") mustEqual "GET"
      result.get.fields("eventType") mustEqual "FastlyDebug"
      result.get.fields("ip") mustEqual "205.160.165.83"
      result.get.fields("statusCode") mustEqual "200"
      result.get.fields("uri") mustEqual "/path/hello.txt"
      result.get.fields("timestamp") mustEqual 1456099208
      result.get.fields("fastlyHost") mustEqual "cache-ord1732"
      result.get.fields("referrer") mustEqual "(null)"
    }

    "correctly parse all formats of HIT/MISS" in {
      val host = "random.example.com"

      val record1 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, HIT http://referrer.com"
      val record2 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT http://referrer.com"
      val record3 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, MISS http://referrer.com"
      val record4 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS http://referrer.com"
      val record5 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS http://referrer.com"
      val record6 = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 MISS, HIT http://referrer.com"

      val res1 = parseRecord(record1,host)
      res1 mustNotEqual None and(res1.get.fields("hitMissShield") mustEqual "HIT") and(res1.get.fields("hitMissEdge") mustEqual "HIT")

      val res2 = parseRecord(record2,host)
      res2 mustNotEqual None and(res2.get.fields("hitMissShield") mustEqual "HIT") and(res2.get.fields("hitMissEdge") mustEqual null)

      val res3 = parseRecord(record3,host)
      res3 mustNotEqual None and(res3.get.fields("hitMissShield") mustEqual "MISS") and(res3.get.fields("hitMissEdge") mustEqual "MISS")

      val res4 = parseRecord(record4,host)
      res4 mustNotEqual None and(res4.get.fields("hitMissShield") mustEqual "MISS") and(res4.get.fields("hitMissEdge") mustEqual null)

      val res5 = parseRecord(record5,host)
      res5 mustNotEqual None and(res5.get.fields("hitMissShield") mustEqual "HIT") and(res5.get.fields("hitMissEdge") mustEqual "MISS")

      val res6 = parseRecord(record6,host)
      res6 mustNotEqual None and(res6.get.fields("hitMissShield") mustEqual "MISS") and(res6.get.fields("hitMissEdge") mustEqual "HIT")

    }

    //-------------------------------------------------------------
    // test.example.com is a specific host config (look at application.test.conf)
    // If you want to test any new fields, trying via a
    // custom hostname is the cleanest way to do it.  Below
    // we introduced a new custom field tcpClientRTT and ensure
    // it is being used from the config and is resolving to the
    // right value for the domain test.example.com
    //--------------------------------------------------------------
    "correctly parse a valid log entry using a specific host config" in {

      val host = "test.example.com"
      val record = "<134>2016-01-08T18:35:59Z cache-atl6234 AmazonS3[168183]: 1.2.3.4 Fri, 08 Jan 2016 18:35:59 GMT GET /path/hello.txt www.domain.com 200 HIT, MISS (null) 25000"

      val result = parseRecord(record,host)
      result mustNotEqual None
      result.get.fields("hostname") mustEqual "www.domain.com"
      result.get.fields("hitMissShield") mustEqual "HIT"
      result.get.fields("hitMissEdge") mustEqual "MISS"
      result.get.fields("httpMethod") mustEqual "GET"
      result.get.fields("eventType") mustEqual "FastlyDebug"
      result.get.fields("ip") mustEqual "1.2.3.4"
      result.get.fields("statusCode") mustEqual "200"
      result.get.fields("uri") mustEqual "/path/hello.txt"
      result.get.fields("timestamp") mustEqual 1452278159
      result.get.fields("fastlyHost") mustEqual "cache-atl6234"
      result.get.fields("referrer") mustEqual "(null)"
      result.get.fields("tcpClientRTT") mustEqual 25000
    }

    "correctly parse a valid log entry with multiple shield entries using a specific host config " in {
      val record ="<134>2016-02-22T00:00:09Z cache-ord1732 AmazonS3[351]: 205.160.165.83 Mon, 22 Feb 2016 00:00:08 GMT GET /path/hello.txt radioedit.example.com 200 MISS, MISS, HIT (null) 25000"
      val host = "test.example.com"

      val result = parseRecord(record,host)
      result mustNotEqual None
      result.get.fields("hostname") mustEqual "radioedit.example.com"
      result.get.fields("hitMissShield") mustEqual "MISS"
      result.get.fields("hitMissEdge") mustEqual "MISS"
      result.get.fields("httpMethod") mustEqual "GET"
      result.get.fields("eventType") mustEqual "FastlyDebug"
      result.get.fields("ip") mustEqual "205.160.165.83"
      result.get.fields("statusCode") mustEqual "200"
      result.get.fields("uri") mustEqual "/path/hello.txt"
      result.get.fields("timestamp") mustEqual 1456099208
      result.get.fields("fastlyHost") mustEqual "cache-ord1732"
      result.get.fields("referrer") mustEqual "(null)"
      result.get.fields("tcpClientRTT") mustEqual 25000
    }

    "correctly parse a user agent field " in {
      val record = "<134>2016-11-08T20:33:21Z cache-atl6227 AmazonS3[204817]: 98.242.211.194 Tue, 08 Nov 2016 20:33:21 GMT GET /path/hello.txt uatest.example.com 200 HIT http://uatext.example.com/ \"Mozilla/5.0 (Linux; Android 5.1.1; SM-T900 Build/LMY47X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.68 Safari/537.36\""
      val host = "uatest.example.com"
      val result = parseRecord(record,host)

      result mustNotEqual None
      result.get.fields("userAgent") mustEqual "Android Chrome"
    }

  }
}