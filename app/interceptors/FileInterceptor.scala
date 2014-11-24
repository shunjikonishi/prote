package interceptors

import models.RequestMessage
import models.ResponseMessage
import models.HttpHeader
import java.io.File

class FileInterceptor(contentType: String, file: File) extends Interceptor {

  override def hookRequest(request: RequestMessage): Either[RequestMessage, ResponseMessage] = {
    val headers = Seq(HttpHeader("Content-Type", contentType))
    Right(ResponseMessage(request, 200, headers, Some(file)))
  }
}