package com.iheart.sqs

import java.net.URLDecoder
import com.iheart.sqs.AmazonHelpers._

object Main {


  /*********************************************
    * This is the function we call with a new
    * SQS Message
  ************************************************/
  def handleMessage(bucket: String, s3key: String) = {
       val key = URLDecoder.decode(s3key,"UTF-8")
       println("Received key " + key)
  }

  def main(args: Array[String]) =
    getSqsMessages
}
