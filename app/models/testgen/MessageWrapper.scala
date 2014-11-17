package models.testgen

import play.api.mvc.BodyParsers
import play.api.libs.json._
import java.net.URLEncoder
import java.net.URLDecoder
import models.HttpMessage
import models.RequestMessage
import models.ResponseMessage
import org.apache.commons.codec.binary.Base64
import jp.co.flect.io.FileUtils

case class MessageWrapper(request: RequestMessage, response: ResponseMessage) {

  def path = request.requestLine.path
  def host = request.host.name
  def protocol = request.host.protocol
  def method = request.requestLine.method

  def hasRequestBody = request.body.isDefined
  def requestContentType = request.contentType

  private def indent(json: JsValue, tab: Int): String = {
    val prefix = "\t" * tab
    val str = Json.prettyPrint(json)
    str.split("\n").map(prefix + _).mkString("\n").substring(tab)
  }

  private def body(msg: HttpMessage, tab: Int): String = {
    def escape(str: String): String = str.replaceAll("\"", "\\\"")
    msg.body.map { f =>
      val data = FileUtils.readFile(f)
      val str = if (msg.isTextBody) new String(data, msg.charset) else Base64.encodeBase64String(data)
println("body: " + str)
      msg.contentType match {
        case "application/x-www-form-urlencoded" =>
          val map = str.split("&").map {  kv =>
            kv.split("=").toList match {
              case key :: value :: Nil =>
                (URLDecoder.decode(key, msg.charset), URLDecoder.decode(key, msg.charset))
              case _ =>
                throw new IllegalStateException(kv)
            }
          }.groupBy(t => t._1)
          indent(JsObject(map.map { case (k, t) =>
            (k, JsArray(t.map(t => JsString(t._2))))
          }.toList), tab)
        case "application/json" => indent(Json.parse(str), tab)
        case _ => "\"" + escape(str) + "\""
      }
    }.getOrElse("")
  }

  def requestHeaders(tab: Int) = {
    indent(request.headersToJson, tab)
  }

  def requestBody(tab: Int): String = body(request, tab)

  def statusCode = response.statusLine.code
}
