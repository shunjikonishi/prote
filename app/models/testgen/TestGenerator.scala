package models.testgen

import models.StorageManager
import java.io.File
import jp.co.flect.io.FileUtils

abstract class TestGenerator(dir: File, sm: StorageManager) {
  srcGen: SourceGenerator =>

  def generate(desc: String, ids: Seq[String]): String = {
    val requests = ids.map(id => MessageWrapper(sm.getRequestMessage(id), sm.getResponseMessage(id)))
    srcGen.doGenerate(desc, requests)
  }
}

object TestGenerator {
  def apply(kind: String): TestGenerator = {
    val dir = new File("test_logs")
    dir.mkdirs
    val sm = StorageManager
    kind match {
      case "mocha" => new TestGenerator(dir, sm) with MochaTestGenerator
      case _ => throw new IllegalArgumentException("Not implemented type: " + kind)
    }
  }
}

