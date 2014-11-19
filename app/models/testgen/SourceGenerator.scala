package models.testgen

import java.io.File
trait SourceGenerator {

  def generateTest(dir: File, name: String, desc: String, messages: Seq[MessageWrapper]): File

}