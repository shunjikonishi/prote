package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumeratee

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
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID
import models.AppConfig
import models.StorageManager
import models.WebSocketManager
import models.CacheManager
import models.HostInfo
import models.testgen.TestGenerator
import exceptions.SSLNotSupportedException

object Application extends Controller {

  def proxy = Action.async(parse.raw) { implicit request =>
    def escape(str: String) = {
      str.foldLeft(new StringBuilder()) { (buf, c) =>
        c match {
          case '|' => buf.append("%7C")
          case _ => buf.append(c)
        }
        buf
      }.toString
    }
    def getHostName(ssl: Boolean): String = {
      request.headers.get("x-forwarded-for")
        .map(_ => request.host)
        .getOrElse {
          if (ssl) {
            request.domain + ":" + AppConfig.httpsPort.getOrElse(throw new SSLNotSupportedException())
          } else {
            request.domain + ":" + AppConfig.httpPort
          }
        }
    } 
    def getRedirectHost(response: Response, hosts: Seq[HostInfo]): Option[HostInfo] = {
      Option(response.getHeader("Location"))
        .filter(_.startsWith("http"))
        .flatMap { str =>
          try {
            val uri = new URI(str)
            val host = uri.getPort match {
              case -1 | 80 | 443 => uri.getHost
              case n => uri.getHost + ":" + n
            }
            hosts.find(_.name == host).map(h => HostInfo(h.name, uri.getScheme == "https"))
          } catch {
            case e: URISyntaxException => None
          }
        }
    }
    val sessionId: String = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)
    val cache = CacheManager(sessionId)
    val hosts = cache.getHosts.getOrElse(AppConfig.targetHost)
    hosts.headOption.map { targetHost =>
      val sm = StorageManager
      val requestId = UUID.randomUUID.toString
      val requestMessage = sm.createRequestMessage(targetHost, request, requestId)
      if (Logger.isDebugEnabled) {
        Logger.debug(requestMessage.toString)
      }

      val ret = Promise[Result]()
      val url = targetHost.protocol + "://" + targetHost.name + escape(request.uri)
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

      val start = System.currentTimeMillis
      proxyReq.execute(new AsyncCompletionHandler[Response](){
        override def onCompleted(response: Response): Response = {
          val responseMessage = sm.createResponseMessage(targetHost, request, response, requestId)
          val time = System.currentTimeMillis - start
          if (Logger.isDebugEnabled) {
            Logger.debug(responseMessage.toString)
          }
          val redirectHost = getRedirectHost(response, hosts)
          val headers = responseMessage.headersToMap.map { case (name, value) =>
            val newValue = if (name.equalsIgnoreCase("Location")) {
              redirectHost.map { host =>
                host.protocol + "://" + 
                  getHostName(host.ssl) +
                  value.substring(value.indexOf(host.name) + host.name.length)
              }.getOrElse(value)
            } else {
              value
            }
            (name, newValue)
          }
          val body = responseMessage.body.map(Enumerator.fromFile(_)).getOrElse(Enumerator.empty)
          val result = (if (responseMessage.isChunked) {
            Status(response.getStatusCode)
              .chunked(body)
              .withHeaders(headers.toSeq:_*)
          } else {
            val header = ResponseHeader(response.getStatusCode, headers)
            Result(header, body)
          }).withCookies(Cookie(AppConfig.cookieName, sessionId))
          ret.success(result)
          WebSocketManager.getInvoker(sessionId).process(requestId, requestMessage, responseMessage, time)

          val nextHost = redirectHost.getOrElse(hosts.head)
          cache.setHosts(nextHost :: hosts.filter(_.name != nextHost.name))
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
    val contextPath = AppConfig.consoleContext
    Ok(views.html.main(sessionId, contextPath)).withCookies(Cookie(AppConfig.cookieName, sessionId))
  }

  def ws = WebSocket.using[String] { implicit request =>
    val sessionId = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(throw new IllegalStateException())
    val h = WebSocketManager.getInvoker(sessionId)
    (h.in, h.out)
  }

  def download(id: String) = Action { implicit request =>
    TestGenerator.findZip(id).map { file =>
      sendFile(file, file.getName)
    }.getOrElse(NotFound)
  }

  private def sendFile(content: File, filename: String): Result = {
    Result(
      ResponseHeader(200, Map(
        CONTENT_LENGTH -> content.length.toString,
        CONTENT_TYPE -> play.api.http.ContentTypes.BINARY,
        CONTENT_DISPOSITION -> "attachment; filename=\"%s\"".format(filename)
      )),
      Enumerator.fromFile(content) &> Enumeratee.onIterateeDone(() => content.delete)
    )
  }

}