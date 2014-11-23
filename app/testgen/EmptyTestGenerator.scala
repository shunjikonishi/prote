package testgen

import java.io.File
import jp.co.flect.io.FileUtils
import models.MessageWrapper

class EmptyTestGenerator extends SourceGenerator {

  override def generateTest(dir: File, name: String, desc: String, messages: Seq[MessageWrapper]): File = {
    val text = "console.log('OK');"
    val ret = new File(dir, name + ".js")
    FileUtils.writeFile(ret, text, "utf-8")
    ret
  }
}

