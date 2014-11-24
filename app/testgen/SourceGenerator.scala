package testgen

import java.io.File
import models.MessageWrapper

trait SourceGenerator {

  def generateTest(dir: File, name: String, desc: String, messages: Seq[MessageWrapper], external: Option[String] = None): File

}