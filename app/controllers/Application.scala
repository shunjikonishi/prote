package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.Enumerator

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Request.EntityWriter
import com.ning.http.client.Response
import com.ning.http.client.AsyncCompletionHandler
import scala.concurrent.{ Future, Promise }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object Application extends Controller {

  val FORCE_HTTPS = true
  val TARGET_HOST = "excel-report2.herokuapp.com"

  def proxy(path: String) = Action.async(parse.raw) { implicit request =>
    def protocol = {
      if (FORCE_HTTPS) {
        "https"
      } else {
        request.headers.get("x-forwarded-proto").getOrElse {
          if (request.secure) "https" else "http"
        }
      }
    }
    val map = Map(
      "uri" -> request.uri,
      "tags" -> request.tags,
      "path" -> request.path,
      "method" -> request.method,
      "version" -> request.version,
      "queryString" -> request.queryString,
      "headers" -> request.headers,
      "remoteAddress" -> request.remoteAddress,
      "secure" -> request.secure
    )
    println("test1 " + map)

    val ret = Promise[Result]()
    val url = protocol + "://" + TARGET_HOST + request.uri
    val headers = request.headers.toMap.flatMap { case (k, v) =>
      if (k.equalsIgnoreCase("Host")) {
        Seq((k, TARGET_HOST))
      } else if (k.equalsIgnoreCase("Origin") || k.equalsIgnoreCase("Referer")) {
        Seq((k, v.head.replace(request.host, TARGET_HOST)))
      } else {
        v.map( v => (k, v))
      }
    }.toSeq

    val client = new AsyncHttpClient()
    val proxyReq = headers.foldLeft(request.method match {
      case "GET" =>client.prepareGet(url)
      case "POST" => client.preparePost(url)
      case "DELETE" => client.prepareDelete(url)
      case "PUT" => client.preparePut(url)
      case "OPTIONS" => client.prepareOptions(url)
      case "HEAD" => client.prepareHead(url)
    }) { (req, kv) =>
println("Header: " + kv._1 + ", " + kv._2)
      req.addHeader(kv._1, kv._2)
    }

    val body = request.body
println("BodySize: " + body.size)
    if (body.size > 0) {
      val src = body.asFile
      val dest = File.createTempFile(src.getName(), ".tmp")
      dest.delete
      dest.deleteOnExit
      src.renameTo(dest)
println("read0: " + dest.length)
      proxyReq.setBody(new EntityWriter() {
        override def writeEntity(os: OutputStream): Unit = {
println("read0: " + dest.length)
          val is = new FileInputStream(dest)
          try {
            val buf = new Array[Byte](8192)
            var n = is.read(buf)
            while (n != -1) {
println("read1: " + new String(buf, 0, n))
              os.write(buf, 0, n)
              n = is.read(buf)
            }
          } finally {
            is.close
            dest.delete
          }
        }
      }, dest.length)
    }

    proxyReq.execute(new AsyncCompletionHandler[Response](){
      override def onCompleted(response: Response): Response = {
        val result = Result(
          ResponseHeader(
            response.getStatusCode,
            mapAsScalaMapConverter(response.getHeaders).asScala.map { case (k, v) =>
              (k, v.get(0))
            }.toMap
          ),
          Enumerator.fromStream(response.getResponseBodyAsStream)
        )
        ret.success(result)
        response
      }
      override def onThrowable(t: Throwable) = {
        ret.failure(t)
      }
    });
    ret.future
  }

  def test = Action { implicit request =>
    Ok("tesst")
  }

}