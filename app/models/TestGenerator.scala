package models

import java.io.File

trait TestGenerator {
  def generate(name: String, desc: String, requests: Seq[String]): File = {
    val ret = File.createTempFile("tmp", name)
    ret.deleteOnExit
    ret
  }

}