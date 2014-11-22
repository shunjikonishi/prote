package models

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.websocket.WebSocket
import com.ning.http.client.websocket.WebSocketUpgradeHandler
import com.ning.http.client.websocket.WebSocketTextListener
import com.ning.http.client.HttpResponseBodyPart
import com.ning.http.client.HttpResponseHeaders
import com.ning.http.client.HttpResponseStatus
import com.ning.http.client.Response
import  com.ning.http.client.providers.netty.NettyResponse
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Concurrent
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class WebSocketProxy(
  client: AsyncHttpClient, request: RequestMessage, 
  onConnect: Response => Unit
) {
  val (out, channel) = Concurrent.broadcast[String]
  val in = Iteratee.foreach[String](ws.sendTextMessage(_))
    .map(_ => ws.close)

  val ws = {
    val protocol = if (request.host.ssl) "wss" else "ws"
    val url = protocol + "://" + request.host.name + request.requestLine.uri
    val req = request.headers.foldLeft(client.prepareGet(url)) { (req, h) =>
      req.addHeader(h.name, h.value)
    }
    req.execute(new MyWebSocketUpgradeHandler()).get()
  }

  private def builder = {
    val builder = new WebSocketUpgradeHandler.Builder()
    builder.addWebSocketListener(new MyWebSocketTextListener())
    builder
  }


  class MyWebSocketTextListener extends WebSocketTextListener {
    private val buf = new StringBuilder()

    override def onFragment(msg: String, last: Boolean) = {
      buf.append(msg)
      if (last) {
        channel.push(buf.toString)
        buf.setLength(0)
      }
    }
    override def onMessage(msg: String) = {
      channel.push(msg)
    }

    override def onOpen(ws: WebSocket) = {
    }

    override def onClose(ws: WebSocket) = {
      channel.eofAndEnd
    }

    override def onError(t: Throwable) = {
      t.printStackTrace
    }
  }

  class MyWebSocketUpgradeHandler extends WebSocketUpgradeHandler(builder) {
    private var status: Option[HttpResponseStatus] = None

    override def onStatusReceived(responseStatus: HttpResponseStatus) = {
      status = Some(responseStatus)
      super.onStatusReceived(responseStatus)
    }
    override def onHeadersReceived(headers: HttpResponseHeaders) = {
      status.map { status =>
        val response = new NettyResponse(status, headers, java.util.Collections.emptyList())
        onConnect(response)
      }
      status = None
      super.onHeadersReceived(headers)
    }

  }
}

object WebSocketProxy {
  def apply(client: AsyncHttpClient, request: RequestMessage)(onConnect: Response => Unit): WebSocketProxy = {
    new WebSocketProxy(client, request, onConnect)
  }
}