package models.testgen

import models.StorageManager

class MochaTestGenerator(sm: StorageManager) extends TestGenerator(sm) {

  protected override def doGenerate(desc: String, messages: Seq[MessageWrapper]): String = {
    views.js.mochaTest(desc, messages).toString
  }
}

object MochaTestGenerator extends MochaTestGenerator(StorageManager)