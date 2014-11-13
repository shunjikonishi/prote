package models

import play.api.mvc.Request
import play.api.mvc.RequestHeader
import play.api.mvc.RawBuffer
import play.api.libs.json._

import java.util.UUID
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import jp.co.flect.io.FileUtils
import com.ning.http.client.Response
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class StorageManager(val dir: File, targetHost: String, cookieName: String) {

  private def createFile(filename: String) = {
    val ret = new File(dir, filename)
    ret.deleteOnExit
    ret
  }

  private def readJson(file: File): JsValue = {
    Json.parse(FileUtils.readFile(file))
  }

  private def parseHeaders(json: JsValue): Seq[HttpHeader] = {
    json \ "headers" match {
      case JsArray(seq) => seq.map { h =>
        val name = (h \ "name").as[String]
        val value = (h \ "value").as[String]
        HttpHeader(name, value)
      }
      case _ => throw new IllegalStateException()
    }
  }

  def getRequestMessage(id: String) = {
    val headerFile = createFile(id +  ".request.headers")
    val bodyFile = createFile(id +  ".request.body")
    val json = readJson(headerFile)

    val requestLine = (json \ "requestLine").as[String]
    val headers = parseHeaders(json)
    RequestMessage(requestLine, headers, bodyFile)
  }

  def getResponseMessage(id: String) = {
    val headerFile = createFile(id +  ".response.headers")
    val bodyFile = createFile(id +  ".response.body")
    val json = readJson(headerFile)

    val statusLine = (json \ "statusLine").as[String]
    val headers = parseHeaders(json)
    ResponseMessage(statusLine, headers, bodyFile)
  }

  def createRequestMessage(request: Request[RawBuffer], id: String): RequestMessage = {
    val requestLine = RequestLine(request.method, request.version, request.uri)
    val headers = request.headers.toMap.flatMap { case (k, v) =>
      if (k.equalsIgnoreCase("Host")) {
        Seq(HttpHeader(k, targetHost))
      } else if (k.equalsIgnoreCase("Origin") || k.equalsIgnoreCase("Referer")) {
        Seq(HttpHeader(k, v.head.replace(request.host, targetHost)))
      } else if (k.equalsIgnoreCase("Cookie")) {
        val replace = cookieName + "=[A-Za-z0-9-]*;?"
        v.map( v => HttpHeader(k, v.replaceFirst(replace, "")))
      } else {
        v.map( v => HttpHeader(k, v))
      }
    }.toSeq
    val body = if (request.body.size > 0) {
      val src = request.body.asFile
      val dest = createFile(id + ".request.body")
      src.renameTo(dest)
      Some(dest)
    } else {
      None
    }
    val ret = RequestMessage(requestLine, headers, body)
    ret.saveHeaders(createFile(id + ".request.headers"))
    ret
  }

  def createResponseMessage(request: RequestHeader, response: Response, id: String): ResponseMessage = {
    val statusLine = StatusLine(response.getStatusCode, request.version, Option(response.getStatusText))
    val headers = mapAsScalaMapConverter(response.getHeaders).asScala.flatMap { case (k, v) =>
      v.map(HttpHeader(k, _))
    }.toSeq
    val body = if (response.hasResponseBody) {
      val bodyFile = createFile(id + ".response.body")
      val is = response.getResponseBodyAsStream
      try {
        val os = new FileOutputStream(bodyFile)
        try {
          val buf = new Array[Byte](8192)
          var n = is.read(buf)
          while (n != -1) {
            os.write(buf, 0, n)
            n = is.read(buf)
          }
        } finally {
          os.close
        }
      } finally {
        is.close
      }
      Some(bodyFile)
    } else {
      None
    }
    val ret = ResponseMessage(statusLine, headers, body)
    ret.saveHeaders(createFile(id + ".response.headers"))
    ret
  }
}

object StorageManager extends StorageManager(
  new File("proxy_logs"), 
  AppConfig.targetHost.getOrElse("unknown"),
  AppConfig.cookieName
) {
  dir.mkdirs
}