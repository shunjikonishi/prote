package models

import play.api.mvc.Cookies
import play.api.libs.json._
import java.io.File
import jp.co.flect.io.FileUtils

abstract class HttpMessage(initialLine: String, headers: Seq[HttpHeader], body: Option[File]) {
  def isRequest: Boolean
  def isResponse = !isRequest

  def isTextBody = {
    val gzip = headers.find { h =>
      "Content-Encoding".equalsIgnoreCase(h.name)
    }.map(_.value.toLowerCase == "gzip").getOrElse(false)
    if (gzip) {
      false
    } else {
      val ct = contentType.toLowerCase
      ct match {
        case str if (str.startsWith("text")) => true
        case str if (str.endsWith("xml")) => true
        case str if (str.endsWith("json")) => true
        case _ => false
      }
    }
  }
  def contentType = headers.find { h =>
    "Content-Type".equalsIgnoreCase(h.name)
  }.map(_.value.takeWhile(_ != ';')).getOrElse("application/octet-stream")

  lazy val headersToMap: Map[String, String] = {
    val (cookies, others) = headers.partition(_.name.toLowerCase == "set-cookie")
    val map = others.map { h =>
      (h.name, h.value)
    }.toMap
    if (cookies.size > 0) {
      map + ("Set-Cookie" -> Cookies.encode(cookies.flatMap(h => Cookies.decode(h.value))))
    } else {
      map
    }
  }

  def toJson = {
    val initialLineKey = if (isRequest) "requestLine" else "statusLine"
    JsObject(Seq(
      initialLineKey -> JsString(initialLine),
      "headers" -> JsArray(headers.map(h => JsObject(Seq(
        "name" -> JsString(h.name),
        "value" -> JsString(h.value)
      ))))
    ))
  }
  def saveHeaders(baseFile: File) = {
    val str = Json.prettyPrint(toJson)
    val middle = if (isRequest) ".request" else ".response"
    FileUtils.writeFile(new File(baseFile.getParentFile, baseFile.getName + middle + ".headers"), str, "utf-8")
  }

  override def toString = {
    val buf = new StringBuilder()
    buf.append(initialLine).append("\r\n")
    headers.foreach(buf.append(_).append("\r\n"))
    buf.append("\r\n")
    body.foreach { f =>
      if (isTextBody) {
        buf.append(FileUtils.readFileAsString(f))
      } else {
        buf.append("(BINARY)")
      }
    }
    buf.toString
  }
}

case class RequestMessage(requestLine: RequestLine, headers: Seq[HttpHeader], body: Option[File])
  extends HttpMessage(requestLine.toString, headers, body) 
{
  val isRequest = true
}

case class ResponseMessage(statusLine: StatusLine, headers: Seq[HttpHeader], body: Option[File])
  extends HttpMessage(statusLine.toString, headers, body) 
{
  val isRequest = false
  def isChunked = {
    headers.find(_.name.equalsIgnoreCase("Transfer-Encoding")).map(_.value.equalsIgnoreCase("chunked")).getOrElse(false)
  }
}

case class RequestLine(method: String, version: String, uri: String) {

  override def toString = s"$method $uri $version"
}

object RequestLine {
  def apply(line: String): RequestLine = {
    line.split(" ").toList match {
      case method :: version :: uri :: Nil => RequestLine(method, version, uri)
      case _ => throw new IllegalArgumentException(line)
    }
  }
}

case class StatusLine(code: Int, version: String, phrase: Option[String]) {

  override def toString = {
    val addition = phrase.map(" " + _).getOrElse("")
    s"$version $code$addition"
  }
}

object StatusLine {
  def apply(line: String): StatusLine = {
    line.split(" ").toList match {
      case code :: version :: Nil => StatusLine(code.toInt, version, None)
      case code :: version :: phrase => StatusLine(code.toInt, version, Some(phrase.mkString(" ")))
      case _ => throw new IllegalArgumentException(line)
    }
  }
}

case class HttpHeader(name: String, value: String) {
  override def toString = s"$name: $value"
}