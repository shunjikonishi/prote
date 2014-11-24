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
import models.ProxyManager
import models.WebSocketProxy
import models.CacheManager
import models.HostInfo
import models.HttpHeader
import models.ResponseMessage
import exceptions.SSLNotSupportedException

object Application extends Controller {

  val pm = ProxyManager
  val client = pm.httpClient

  def proxy = Action.async(parse.raw) { implicit request =>
    val sessionId: String = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)

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
    def toResult(response: ResponseMessage): Result = {
      val headers = response.headersToMap
      val body = response.body.map(Enumerator.fromFile(_)).getOrElse(Enumerator.empty)
      val result = response.isChunked match {
        case true =>
          Status(response.statusLine.code)
            .chunked(body)
            .withHeaders(headers.toSeq:_*)
        case false =>
          val header = ResponseHeader(response.statusLine.code, headers)
          Result(header, body)
      }
      result.withCookies(Cookie(AppConfig.cookieName, sessionId))
    }
    val cache = CacheManager(sessionId)
    val hosts = cache.getHosts.getOrElse(AppConfig.targetHost)
    hosts.headOption.map { targetHost =>
      val sm = pm.storageManager
      val requestId = UUID.randomUUID.toString
      val requestMessage = sm.createRequestMessage(targetHost, request, requestId)
      val interceptor = pm.interceptor(request.path)

      val ret = Promise[Result]()
      interceptor.hookRequest(requestMessage) match {
        case Left(requestMessage) =>
          val url = targetHost.protocol + "://" + targetHost.name + escape(request.uri)
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
                  Iterator.continually(is.read(buf)).takeWhile(_ != -1).foreach (os.write(buf, 0, _))
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
              val redirectHost = getRedirectHost(response, hosts)
              val rewriteMessage = interceptor.hookResponse(
                requestMessage, 
                redirectHost.map { host =>
                  responseMessage.copy(headers = responseMessage.headers.map { h =>
                    if (h.is("Location")) {
                      val newValue = host.protocol + "://" + 
                        getHostName(host.ssl) +
                        h.value.substring(h.value.indexOf(host.name) + host.name.length)
                      HttpHeader(h.name, newValue)
                    } else {
                      h
                    }
                  })
                }.getOrElse(responseMessage)
              )
              ret.success(toResult(rewriteMessage))
              pm.console(sessionId).process(requestId, requestMessage, responseMessage, time)
              val nextHost = redirectHost.getOrElse(hosts.head)
              cache.setHosts(nextHost :: hosts.filter(_.name != nextHost.name))
              response
            }
            override def onThrowable(t: Throwable) = {
              ret.failure(t)
            }
          });
        case Right(responseMessage) =>
          responseMessage.copyTo(sm.dir, requestId)
          ret.success(toResult(responseMessage))
          pm.console(sessionId).process(requestId, requestMessage, responseMessage, 0)
      }
      ret.future
    }.getOrElse {
      Future.successful(Ok("TARGET_HOST is not defined."))
    }
  }

  def proxyWS = WebSocket.using[String] { implicit request =>
    val sessionId: String = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)
    val cache = CacheManager(sessionId)
    val hosts = cache.getHosts.getOrElse(AppConfig.targetHost)
    val h = hosts.headOption.map { targetHost =>
      val sm = pm.storageManager
      val requestId = UUID.randomUUID.toString
      val requestMessage = sm.createRequestMessage(targetHost, Request(request, RawBuffer(0)), requestId)
      val start = System.currentTimeMillis
      pm.webSocketProxy(sessionId, requestMessage) { response =>
        val responseMessage = sm.createResponseMessage(targetHost, request, response, requestId)
        val time = System.currentTimeMillis - start
        pm.console(sessionId).process(requestId, requestMessage, responseMessage, time)
      }
    } getOrElse {
      throw new IllegalStateException()
    }
    (h.in, h.out)
  }

  def main = Action { implicit request =>
    val sessionId = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(UUID.randomUUID.toString)
    val contextPath = AppConfig.consoleContext
    Ok(views.html.main(sessionId, contextPath, pm.generators)).withCookies(
      Cookie(AppConfig.cookieName, sessionId)
    )
  }

  def ws = WebSocket.using[String] { implicit request =>
    val sessionId = request.cookies.get(AppConfig.cookieName).map(_.value).getOrElse(throw new IllegalStateException())
    val h = pm.console(sessionId)
    (h.in, h.out)
  }

  def download(id: String) = Action { implicit request =>
    pm.findZip(id).map { file =>
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