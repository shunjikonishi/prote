package models

import play.api.mvc.Cookies
import play.api.libs.json._
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import jp.co.flect.io.FileUtils

trait HttpMessage {
  val host: HostInfo
  def initialLine: String
  val headers: Seq[HttpHeader]
  val body: Option[File]
  def isRequest: Boolean
  def isResponse = !isRequest

  def messageType = if (isRequest) "request" else "response"

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

  def getBodyAsText: String = {
    body.map(f => FileUtils.readFileAsString(f, "utf-8")).getOrElse(throw new IllegalStateException())
  }

  def getBodyAsJson: JsValue = {
    val text = getBodyAsText
    Json.parse(text)
  }

  def getBodyAsUrlEncoded: Map[String, Seq[String]] = {
    import play.core.parsers.FormUrlEncodedParser
    val text = getBodyAsText
    FormUrlEncodedParser.parse(text, charset)
  }

  protected def doReplaceBody(text: String): Seq[HttpHeader] = {
    body.map { f =>
      FileUtils.writeFile(f, text.getBytes(charset))
      val newLength = f.length.toString
      getHeader("Content-Length").filter(_.value != newLength).map { h =>
        val newHeaders = headers.map(h => if (h.is("Content-Length")) HttpHeader(h.name, newLength) else h)
        val json = toJson.deepMerge(JsObject(Seq(
          "headers" -> JsArray(newHeaders.map(h => JsObject(Seq(
            "name" -> JsString(h.name),
            "value" -> JsString(h.value)
          ))))
        )))
        val str = Json.prettyPrint(json)
        val headerFile = new File(f.getParentFile, f.getName.takeWhile(_ != '.') + "." + messageType + ".headers")
        FileUtils.writeFile(headerFile, str, "utf-8")
        newHeaders
      }.getOrElse(headers)
    }.getOrElse(throw new IllegalArgumentException())
  }

  protected def doReplaceBody(json: JsValue): Seq[HttpHeader] = {
    doReplaceBody(Json.stringify(json))
  }

  protected def doReplaceBody(parameters: Map[String, Seq[String]]): Seq[HttpHeader] = {
    val cs = charset
    val text = parameters.map { case (key, seq) =>
      val encodedKey = URLEncoder.encode(key, cs)
      seq.map(encodedKey + "=" + URLEncoder.encode(_, cs)).mkString("&")
    }.mkString("&")
    doReplaceBody(text)
  }

  def getHeader(name: String): Option[HttpHeader] = headers.find(_.is(name))

  def contentType = getHeader("Content-Type").map(_.withoutAttribute).getOrElse("application/octet-stream")

  def charset = {
    getHeader("Content-Type").flatMap(_.attribute("charset")).getOrElse("utf-8")
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

case class RequestMessage(host: HostInfo, requestLine: RequestLine, headers: Seq[HttpHeader], body: Option[File])
  extends HttpMessage
{
  val isRequest = true
  def initialLine = requestLine.toString

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

  def replaceBody(text: String): RequestMessage = copy(headers=doReplaceBody(text))
  def replaceBody(json: JsValue): RequestMessage = copy(headers=doReplaceBody(json))
  def replaceBody(parameters: Map[String, Seq[String]]): RequestMessage = copy(headers=doReplaceBody(parameters))

}

object RequestMessage {
  def apply(host: HostInfo, requestLine: String, headers: Seq[HttpHeader], body: File): RequestMessage = {
    RequestMessage(host, RequestLine(requestLine), headers, body.exists match {
      case true => Some(body)
      case false => None
    })
  }
}

case class ResponseMessage(host: HostInfo, statusLine: StatusLine, headers: Seq[HttpHeader], body: Option[File])
  extends HttpMessage
{
  val isRequest = false
  def initialLine = statusLine.toString

  def replaceBody(text: String): ResponseMessage = copy(headers=doReplaceBody(text))
  def replaceBody(json: JsValue): ResponseMessage = copy(headers=doReplaceBody(json))
  def replaceBody(parameters: Map[String, Seq[String]]): ResponseMessage = copy(headers=doReplaceBody(parameters))
}

object ResponseMessage {
  def apply(request: RequestMessage, statusCode: Int, headers: Seq[HttpHeader], body: Option[File]): ResponseMessage = {
    val statusLine = StatusLine(statusCode, request.requestLine.version)
    ResponseMessage(request.host, statusLine, headers, body)
  }

  def apply(host: HostInfo, statusLine: String, headers: Seq[HttpHeader], body: Option[File]): ResponseMessage = {
    ResponseMessage(host, StatusLine(statusLine), headers, body)
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

case class StatusLine(code: Int, version: String, phrase: Option[String] = None) {

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