package models

import play.api.mvc.Request
import play.api.mvc.RawBuffer

import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import jp.co.flect.io.FileUtils
import com.ning.http.client.Response
import scala.collection.JavaConverters._

class StorageManager(val dir: File, targetHost: String, cookieName: String) {

  def newBaseFile = new File(dir, UUID.randomUUID.toString)

  def createRequestMessage(request: Request[RawBuffer], baseFile: File): RequestMessage = {
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
      val dest = new File(baseFile.getParentFile, baseFile.getName + ".request.body")
      src.renameTo(dest)
      Some(dest)
    } else {
      None
    }
    RequestMessage(requestLine, headers, body)
  }

  def createResponseMessage(httpVersion: String, response: Response, baseFile: File): ResponseMessage = {
    val statusLine = StatusLine(response.getStatusCode, httpVersion, response.getStatusText)
    val headers = mapAsScalaMapConverter(response.getHeaders).asScala.map { case (k, v) =>
      HttpHeader(k, v.get(0))
    }.toSeq
    val body = if (response.hasResponseBody) {
      val bodyFile = new File(baseFile.getParentFile, baseFile.getName + ".response.body")
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
    ResponseMessage(statusLine, headers, body)
  }
}

object StorageManager extends StorageManager(
  new File("proxy_logs"), 
  AppConfig.targetHost.getOrElse("unknown"),
  AppConfig.cookieName
) {
  dir.mkdirs
}