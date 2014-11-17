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

class StorageManager(val dir: File, cookieName: String) {

  private def createFile(filename: String) = {
    val ret = new File(dir, filename)
    ret
  }

  private def readJson(file: File): JsValue = {
    Json.parse(FileUtils.readFile(file))
  }

  private def parseHeaders(json: JsValue): (HostInfo, Seq[HttpHeader]) = {
    val host = HostInfo(
      (json \ "host").as[String],
      (json \ "protocol").as[String] == "https"
    )
    val headers = json \ "headers" match {
      case JsArray(seq) => seq.map { h =>
        val name = (h \ "name").as[String]
        val value = (h \ "value").as[String]
        HttpHeader(name, value)
      }
      case _ => throw new IllegalStateException()
    }
    (host, headers)
  }

  def getRequestMessage(id: String) = {
    val headerFile = createFile(id +  ".request.headers")
    val bodyFile = createFile(id +  ".request.body")
    val json = readJson(headerFile)

    val requestLine = (json \ "requestLine").as[String]
    val (host, headers) = parseHeaders(json)
    RequestMessage(host, requestLine, headers, bodyFile)
  }

  def getResponseMessage(id: String) = {
    val headerFile = createFile(id +  ".response.headers")
    val bodyFile = createFile(id +  ".response.body")
    val json = readJson(headerFile)

    val statusLine = (json \ "statusLine").as[String]
    val (host, headers) = parseHeaders(json)
    ResponseMessage(host, statusLine, headers, bodyFile)
  }

  def createRequestMessage(host: HostInfo, request: Request[RawBuffer], id: String, deleteOnExit: Boolean = true): RequestMessage = {
    val requestLine = RequestLine(request.method, request.version, request.uri)
    val headers = request.headers.toMap.flatMap { case (k, v) =>
      if (k.equalsIgnoreCase("Host")) {
        Seq(HttpHeader(k, host.name))
      } else if (k.equalsIgnoreCase("Origin") || k.equalsIgnoreCase("Referer")) {
        Seq(HttpHeader(k, v.head.replace(request.host, host.name)))
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
      if (deleteOnExit) dest.deleteOnExit
      Some(dest)
    } else {
      None
    }
    val ret = RequestMessage(host, requestLine, headers, body)
    val headerFile = ret.saveHeaders(createFile(id + ".request.headers"))
    if (deleteOnExit) headerFile.deleteOnExit
    ret
  }

  def createResponseMessage(host: HostInfo, request: RequestHeader, response: Response, id: String, deleteOnExit: Boolean = true): ResponseMessage = {
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
      if (deleteOnExit) bodyFile.deleteOnExit
      Some(bodyFile)
    } else {
      None
    }
    val ret = ResponseMessage(host, statusLine, headers, body)
    val headerFile = ret.saveHeaders(createFile(id + ".response.headers"))
    if (deleteOnExit) headerFile.deleteOnExit
    ret
  }

  def getFile(filename: String): Option[File] = {
    val file = new File(dir, filename)
    if (file.exists) Some(file) else None
  }

  def saveToFile(filename: String, text: String): File = {
    val file = createFile(filename)
    FileUtils.writeFile(file, text, "utf-8")
    file
  }
}

object StorageManager extends StorageManager(
  new File("proxy_logs"), 
  AppConfig.cookieName
) {
  dir.mkdirs
}