package com.iheart.sqs

import com.iheart.sqs.AmazonHelpers._

object Main {

  def main(args: Array[String]) = {
    DBUtils.startTimer()
    DBUtils.startDataDogTimer()
    getSqsMessages
  }
}
