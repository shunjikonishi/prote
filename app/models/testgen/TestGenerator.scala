package models.testgen

import models.StorageManager
import java.io.File
import jp.co.flect.io.FileUtils

abstract class TestGenerator(sm: StorageManager) {
  
  protected def doGenerate(desc: String, messages: Seq[MessageWrapper]): String

  def generate(desc: String, requests: Seq[String]): String = {
    doGenerate(desc, requests.map(id => MessageWrapper(sm.getRequestMessage(id), sm.getResponseMessage(id))))
  }
}

