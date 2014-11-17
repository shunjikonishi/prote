package models.testgen

import models.StorageManager

class MochaTestGenerator(sm: StorageManager) extends TestGenerator(sm) {

  override protected def doGenerate(desc: String, messages: Seq[MessageWrapper]): String = {
    views.txt.mochaTest(desc, messages).toString
  }
}

object MochaTestGenerator extends MochaTestGenerator(StorageManager)