package models

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Response
import java.io.File

class ProxyManager {

  val httpClient = new AsyncHttpClient()

  val proxyLogs = new File("proxy_logs")
  val testLogs = new File("test_logs")

  proxyLogs.mkdirs
  testLogs.mkdirs

  val storageManager = new StorageManager(proxyLogs, AppConfig.cookieName)

  private var invokerMap: Map[String, WebSocketInvoker] = Map.empty

  def getInvoker(sessionId: String): WebSocketInvoker = {
    invokerMap.get(sessionId).filter(!_.closed) match {
      case Some(x) => x
      case None =>
        val ret = new WebSocketInvoker(this, sessionId)
        invokerMap += (sessionId -> ret)
        ret
    }
  }

  def webSocketProxy(sessionId: String, request: RequestMessage)(onConnect: Response => Unit) = {
    new WebSocketProxy(this, sessionId, request, onConnect)
  }
}

object ProxyManager extends ProxyManager
