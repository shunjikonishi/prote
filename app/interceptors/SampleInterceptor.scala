package interceptors

import models.RequestMessage
import models.ResponseMessage
import play.api.libs.json._

class SampleInterceptor extends Interceptor {

  override def hookRequest(request: RequestMessage): Either[RequestMessage, ResponseMessage] = {
    val map = request.getBodyAsUrlEncoded ++ Map(
      "username" -> Seq("test@flect.co.jp"),
      "password" -> Seq("password")
    )
    Left(request.replaceBody(map))
  }
}