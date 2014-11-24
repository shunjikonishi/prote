package testgen

import java.io.File
import jp.co.flect.io.FileUtils
import models.MessageWrapper

class MochaTestGenerator extends SourceGenerator {

  override def generateTest(dir: File, name: String, desc: String, messages: Seq[MessageWrapper], external: Option[String] = None): File = {
    val text = views.txt.mochaTest(desc, messages, external).toString
    val ret = new File(dir, name + ".js")
    FileUtils.writeFile(ret, text, "utf-8")
    ret
  }
}

