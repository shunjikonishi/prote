package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.Enumerator

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Response
import com.ning.http.client.AsyncCompletionHandler
import scala.concurrent.{ Future, Promise }

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.FileInputStream

object Application extends Controller {

  val FORCE_HTTPS = true
  val TARGET_HOST = "excelreport.net"

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
    val optStream = if (body.size > 0) Some(new FileInputStream(body.asFile)) else None
    optStream.foreach { is =>
      println("file = " + body.asFile)
      println(java.nio.file.Files.readAllLines(body.asFile.toPath, java.nio.charset.Charset.forName("utf-8")))
      proxyReq.setBody(is)
    }

println(optStream)

    proxyReq.execute(new AsyncCompletionHandler[Response](){
      override def onCompleted(response: Response): Response = {
        optStream.foreach(_.close)
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
        optStream.foreach(_.close)
        ret.failure(t)
      }
    });
    ret.future
  }

}