package models.testgen

import models.StorageManager

class MochaTestGenerator(sm: StorageManager) extends TestGenerator(sm) {

  protected override def doGenerate(desc: String, messages: Seq[MessageWrapper]): String = {
    val dir = new java.io.File("test")
    messages.zipWithIndex.foreach { case(msg, idx) =>
      msg.request.copyTo(dir, idx.toString)
      msg.response.copyTo(dir, idx.toString)
    }
    views.txt.mochaTest(desc, messages).toString
  }
}

object MochaTestGenerator extends MochaTestGenerator(StorageManager)