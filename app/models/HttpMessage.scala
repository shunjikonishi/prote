package models

import java.io.File
import jp.co.flect.io.FileUtils

abstract class HttpMessage(firstLine: String, headers: Seq[HttpHeader], body: Option[File]) {
  def isTextBody = {
    val ct = contentType.toLowerCase
    ct match {
      case str if (str.startsWith("text")) => true
      case str if (str.endsWith("xml")) => true
      case str if (str.endsWith("json")) => true
      case _ => false
    }
  }
  def contentType = headers.find { h =>
    "Content-Type".equalsIgnoreCase(h.name)
  }.map(_.value.takeWhile(_ != ';')).getOrElse("application/octet-stream")

  def headersToMap: Map[String, String] = {
    headers.map { h =>
      (h.name, h.value)
    }.toMap
  }

  override def toString = {
    val buf = new StringBuilder()
    buf.append(firstLine).append("\r\n")
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

case class ResponseMessage(statusLine: StatusLine, headers: Seq[HttpHeader], body: Option[File])
  extends HttpMessage(statusLine.toString, headers, body)

case class RequestLine(method: String, version: String, uri: String) {
  override def toString = s"$method $uri $version"
}

case class StatusLine(code: Int, version: String, phrase: String) {
  override def toString = s"$version $code $phrase"
}

case class HttpHeader(name: String, value: String) {
  override def toString = s"$name: $value"
}