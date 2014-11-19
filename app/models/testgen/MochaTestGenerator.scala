package models.testgen

import models.StorageManager

trait MochaTestGenerator extends SourceGenerator {

  override def doGenerate(desc: String, messages: Seq[MessageWrapper]): String = {
    views.txt.mochaTest(desc, messages).toString
  }
}

