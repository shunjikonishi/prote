package models

import play.api.cache.Cache
import play.api.Play.current

class CacheManager(sessionId: String) {
  def getHosts: Option[List[HostInfo]] = Cache.getAs[List[HostInfo]](sessionId + "-hosts")
  def setHosts(hosts: List[HostInfo]) = Cache.set(sessionId + "-hosts", hosts, 60 * 60 * 2)
}

object CacheManager {
  def apply(sessionId: String) = new CacheManager(sessionId)
}