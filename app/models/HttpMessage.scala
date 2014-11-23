package models

import play.api.mvc.Cookies
import play.api.libs.json._
import java.io.File
import java.net.URLDecoder
import jp.co.flect.io.FileUtils

abstract class HttpMessage(val host: HostInfo, initialLine: String, val headers: Seq[HttpHeader], val body: Option[File]) {
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
        case str if (str == "application/x-www-form-urlencoded") => true
        case str if (str.startsWith("text")) => true
        case str if (str.endsWith("xml")) => true
        case str if (str.endsWith("json")) => true
        case str if (str.endsWith("javascript")) => true
        case str if (str.endsWith("stylesheet")) => true
        case _ => false
      }
    }
  }

  def getHeader(name: String): Option[String] = headers.find(h => name.equalsIgnoreCase(h.name)).map(_.value)

  def contentType = getHeader("Content-Type").map(_.takeWhile(_ != ';')).getOrElse("application/octet-stream")

  def charset = {
    getHeader("Content-Type")
      .flatMap(_.split(";").find(_.toLowerCase.startsWith("charset=")))
      .map(_.split("=")(1))
      .getOrElse("utf-8")
  }

  def headersToMap: Map[String, String] = {
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

  def headersToJson = JsObject(headers.map { h =>
    (h.name, JsString(h.value))
  })

  def isChunked = {
    headers.find(_.name.equalsIgnoreCase("Transfer-Encoding"))
      .map(_.value.equalsIgnoreCase("chunked"))
      .getOrElse(false)
  }

  def toJson = {
    val initialLineKey = if (isRequest) "requestLine" else "statusLine"
    JsObject(Seq(
      "host" -> JsString(host.name),
      "protocol" -> JsString(host.protocol),
      initialLineKey -> JsString(initialLine),
      "headers" -> JsArray(headers.map(h => JsObject(Seq(
        "name" -> JsString(h.name),
        "value" -> JsString(h.value)
      ))))
    ))
  }
  def saveHeaders(file: File): File = {
    val str = Json.prettyPrint(toJson)
    FileUtils.writeFile(file, str, "utf-8")
    file
  }

  def kind = {
    body.map { file =>
      contentType.toLowerCase match {
        case str if (str == "application/x-www-form-urlencoded") => MessageKind.UrlEncoded
        case str if (str.startsWith("image/")) => MessageKind.Image
        case str if (str.endsWith("/javascript")) => MessageKind.Script
        case str if (str.endsWith("/css")) => MessageKind.Script
        case str if (str.endsWith("json")) => MessageKind.Json
        case str if (str.endsWith("html")) => MessageKind.HTML
        case str if (str.endsWith("xml")) => MessageKind.XML
        case _ => MessageKind.Unknown
      }
    }.getOrElse(MessageKind.None)
  }

  override def toString: String = toString(false)

  def toString(expand: Boolean): String = {
    val buf = new StringBuilder()
    buf.append(initialLine).append("\r\n")
    headers.foreach(buf.append(_).append("\r\n"))
    buf.append("\r\n")
    body.foreach { f =>
      if (isTextBody) {
        val str = FileUtils.readFileAsString(f)
        if (expand && kind == MessageKind.Json) {
          buf.append(Json.prettyPrint(Json.parse(str)))
        } else if (expand && kind == MessageKind.UrlEncoded) {
          str.split("&").foreach { kv =>
            val cs = charset
            kv.split("=").toList match {
              case k :: v :: Nil =>
                buf.append(URLDecoder.decode(k, cs))
                  .append("=")
                  .append(URLDecoder.decode(v, cs)).append("\n")
              case k :: Nil =>
                buf.append(URLDecoder.decode(k, cs))
                  .append("=").append("\n")
              case _ =>
                buf.append(URLDecoder.decode(kv, cs)).append("\n")
            }
          }
        } else {
          buf.append(str)
        }
      } else {
        buf.append("(BINARY)")
      }
    }
    buf.toString
  }

  def copyTo(dest: File, newId: String) = {
    val middle = if (isRequest) ".request" else ".response"
    saveHeaders(new File(dest, newId + middle + ".headers"))
    body.foreach(f => FileUtils.copy(f, new File(dest, newId + middle + ".body")))
  }
}

case class RequestMessage(override val host: HostInfo, requestLine: RequestLine, override val headers: Seq[HttpHeader], override val body: Option[File])
  extends HttpMessage(host, requestLine.toString, headers, body) 
{
  val isRequest = true

  def responseKindFromPath = {
    requestLine.path.toLowerCase match {
      case str if (str.endsWith(".js")) => MessageKind.Script
      case str if (str.endsWith(".js.map")) => MessageKind.Script
      case str if (str.endsWith(".css")) => MessageKind.Script

      case str if (str.endsWith(".png")) => MessageKind.Image
      case str if (str.endsWith(".jpeg")) => MessageKind.Image
      case str if (str.endsWith(".jpg")) => MessageKind.Image
      case str if (str.endsWith(".gif")) => MessageKind.Image
      case str if (str.endsWith(".svg")) => MessageKind.Image

      case str if (str.endsWith(".html")) => MessageKind.HTML
      case str if (str.endsWith(".htm")) => MessageKind.HTML
      case str if (str.endsWith(".xhtml")) => MessageKind.HTML

      case str if (str.endsWith(".json")) => MessageKind.Json
      case str if (str.endsWith(".xml")) => MessageKind.XML

      case _ => MessageKind.Unknown
    }
  }

}

object RequestMessage {
  def apply(host: HostInfo, requestLine: String, headers: Seq[HttpHeader], body: File): RequestMessage = {
    RequestMessage(host, RequestLine(requestLine), headers, body.exists match {
      case true => Some(body)
      case false => None
    })
  }
}

case class ResponseMessage(override val host: HostInfo, statusLine: StatusLine, override val headers: Seq[HttpHeader], override val body: Option[File])
  extends HttpMessage(host, statusLine.toString, headers, body) 
{
  val isRequest = false
}

object ResponseMessage {
  def apply(host: HostInfo, statusLine: String, headers: Seq[HttpHeader], body: File): ResponseMessage = {
    ResponseMessage(host, StatusLine(statusLine), headers, body.exists match {
      case true => Some(body)
      case false => None
    })
  }
}


case class RequestLine(method: String, version: String, uri: String) {

  def path = uri.takeWhile(_ != '?')
  override def toString = s"$method $uri $version"
}

object RequestLine {
  def apply(line: String): RequestLine = {
    line.split(" ").toList match {
      case method :: uri :: version :: Nil => RequestLine(method, version, uri)
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
      case version :: code :: Nil => StatusLine(code.toInt, version, None)
      case version :: code :: phrase => StatusLine(code.toInt, version, Some(phrase.mkString(" ")))
      case _ => throw new IllegalArgumentException(line)
    }
  }
}

sealed abstract class MessageKind(name: String) {
  override def toString = name
}
object MessageKind {
  case object None    extends MessageKind("None")
  case object Image   extends MessageKind("Image")
  case object Script  extends MessageKind("Script")
  case object HTML    extends MessageKind("HTML")
  case object Json    extends MessageKind("Json")
  case object XML     extends MessageKind("XML")
  case object UrlEncoded extends MessageKind("UrlEncoded")
  case object Unknown extends MessageKind("Unknown")
}