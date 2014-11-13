package models.testgen

import models.StorageManager
import java.io.File

trait TestGenerator {
  val sm: StorageManager
  val ssl: Boolean
  
  def generate(name: String, desc: String, requests: Seq[String]): File = {
    val ret = File.createTempFile("tmp", name)
    ret.deleteOnExit
    ret
  }
}

