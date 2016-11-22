package com.iheart.sqs

import java.util.regex.Pattern

object UserAgent {

  val bot = Pattern.compile("(ads|google|bing|msn|yandex|baidu|ro|career|)bot")
  val spider = Pattern.compile("(baidu|jike|symantec)spider")
  val scanner = Pattern.compile("scanner")
  val crawler = Pattern.compile("(web)crawler")

  val ipad = Pattern.compile("ipad")
  val iphone = Pattern.compile("ip(hone|od)")
  val androidPhone = Pattern.compile("android.*(mobile|mini)")
  val androidTablet = Pattern.compile("android")
  val operaMobile = Pattern.compile("Opera Mobi")
  val msPhone = Pattern.compile("IEMobile")
  val gingerBread = Pattern.compile("GT-.*Build/GINGERBREAD")

  def isBot(str: String) = bot.matcher(str).find() || spider.matcher(str).find() || scanner.matcher(str).find() || crawler.matcher(str).find()

  def isIpad(str: String) = ipad.matcher(str).find()

  def isiPhone(str: String) = iphone.matcher(str).find()

  def isAndroidPhone(str: String) = androidPhone.matcher(str).find() || operaMobile.matcher(str).find() || gingerBread.matcher(str).find()

  def isAndroidTablet(str: String) = androidTablet.matcher(str).find()

  def isMSPhone(str: String) = msPhone.matcher(str).find()



}
