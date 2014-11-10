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
import java.util.UUID
import models.AppConfig
import models.StorageManager

object Application extends Controller {

  def proxy = Action.async(parse.raw) { implicit request =>
    def protocol = {
      if (AppConfig.forceSSL) {
        "https"
      } else {
        request.headers.get("x-forwarded-proto").getOrElse {
          if (request.secure) "https" else "http"
        }
      }
    }
    AppConfig.targetHost.map { targetHost =>
      val sessionId: String = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)
      val sm = StorageManager
      val baseFile = sm.newBaseFile
      val requestMessage = sm.createRequestMessage(request, baseFile)
      if (Logger.isDebugEnabled) {
        Logger.debug(requestMessage.toString)
        Logger.debug("")
      }

      val ret = Promise[Result]()
      val url = protocol + "://" + targetHost + request.uri
      val client = new AsyncHttpClient()
      val proxyReq = requestMessage.headers.foldLeft(request.method match {
        case "GET" =>client.prepareGet(url)
        case "POST" => client.preparePost(url)
        case "DELETE" => client.prepareDelete(url)
        case "PUT" => client.preparePut(url)
        case "OPTIONS" => client.prepareOptions(url)
        case "HEAD" => client.prepareHead(url)
      }) { (req, h) =>
        req.addHeader(h.name, h.value)
      }
      requestMessage.body.foreach { file =>
        proxyReq.setBody(new EntityWriter() {
          override def writeEntity(os: OutputStream): Unit = {
            val is = new FileInputStream(file)
            try {
              val buf = new Array[Byte](8192)
              var n = is.read(buf)
              while (n != -1) {
                os.write(buf, 0, n)
                n = is.read(buf)
              }
            } finally {
              is.close
            }
          }
        }, file.length)
      }

      proxyReq.execute(new AsyncCompletionHandler[Response](){
        override def onCompleted(response: Response): Response = {
          val responseMessage = sm.createResponseMessage(request.version, response, baseFile)
          if (Logger.isDebugEnabled) {
            Logger.debug(responseMessage.toString)
            Logger.debug("")
          }
          val body = responseMessage.body.map(Enumerator.fromFile(_)).getOrElse(Enumerator.empty)
          val result = (if (responseMessage.isChunked) {
            Status(response.getStatusCode)
              .chunked(body)
              .withHeaders(responseMessage.headersToMap.toSeq:_*)
          } else {
            val header = ResponseHeader(response.getStatusCode, responseMessage.headersToMap)
            Result(header, body)
          }).withCookies(Cookie(AppConfig.cookieName, sessionId))
          ret.success(result)
          response
        }
        override def onThrowable(t: Throwable) = {
          ret.failure(t)
        }
      });
      ret.future
    }.getOrElse {
      Future.successful(Ok("TARGET_HOST is not defined."))
    }
  }

  def main = Action { implicit request =>
    val sessionId = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)
    Ok(views.html.main(sessionId)).withCookies(Cookie(AppConfig.cookieName, sessionId))
  }

}