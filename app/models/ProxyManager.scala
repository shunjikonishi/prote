package models

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.Response
import java.io.File
import testgen.SourceGeneratorRegistry
import interceptors.InterceptorRegistry
import interceptors.Interceptor

class ProxyManager {

  val httpClient = new AsyncHttpClient()

  val proxyLogs = new File("proxy_logs")
  val testLogs = new File("test_logs")

  proxyLogs.mkdirs
  testLogs.mkdirs

  val storageManager = new StorageManager(proxyLogs, AppConfig.cookieName)

  private var invokerMap: Map[String, Console] = Map.empty

  def console(sessionId: String): Console = {
    invokerMap.get(sessionId).filter(!_.closed) match {
      case Some(x) => x
      case None =>
        val ret = new Console(this, sessionId)
        invokerMap += (sessionId -> ret)
        ret
    }
  }

  def interceptor(path: String): Interceptor = InterceptorRegistry.get(path)

  def generators: Seq[(String, String)] = SourceGeneratorRegistry.generators.toSeq

  def testGenerator(name: String) = {
    new TestGenerator(testLogs, storageManager, SourceGeneratorRegistry.get(name))
  }

  def regenerator(id: String, name: String): Option[TestGenerator] = {
    val testDir = new File(testLogs, id)
    Option(testDir).filter(_.exists).map { f =>
      val sm = new StorageManager(f)
      new TestGenerator(testLogs, sm, SourceGeneratorRegistry.get(name))
    }
  }

  def findZip(id: String): Option[File] = {
    val dir = new File(testLogs, id)
    Option(dir)
      .filter(_.exists)
      .flatMap(_.listFiles.find(_.getName.endsWith(".zip")))
  }

  def webSocketProxy(sessionId: String, request: RequestMessage)(onConnect: Response => Unit) = {
    new WebSocketProxy(this, sessionId, request, onConnect)
  }
}

object ProxyManager extends ProxyManager
