package models

import play.api.Play
import play.api.Play.current

object AppConfig {
  val DEFAULT_CONTEXT = "CONSOLE"
  val DEFAULT_COOKIE = "PROTE_SESSION"

  val targetHost: List[HostInfo] = Play.configuration.getString("target.host").map{ 
    _.split(",").map(s => HostInfo(s.trim, false)).toList
  }.getOrElse(Nil)
  val consoleContext = Play.configuration.getString("console.context").getOrElse(DEFAULT_CONTEXT)
  val cookieName = Play.configuration.getString("cookie.name").getOrElse(DEFAULT_COOKIE)
  val httpPort = Play.configuration.getInt("http.port").getOrElse(9000)
  val httpsPort = Play.configuration.getInt("https.port")
}