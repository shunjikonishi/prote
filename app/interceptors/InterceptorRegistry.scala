package interceptors

import java.util.regex.Pattern

object InterceptorRegistry {  

  val defaultInterceptor = new Interceptor() {}

  private val list: Seq[(Pattern, Interceptor)] = Seq(
    ".*" -> defaultInterceptor
  ).map{case (s, v) => (s.r.pattern, v)}


  def get(path: String): Interceptor = {
    list.find{case (p, v) =>  p.matcher(path).matches()}
      .map(_._2).getOrElse(defaultInterceptor)
  }
}