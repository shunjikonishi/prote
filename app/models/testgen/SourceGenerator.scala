package models.testgen

trait SourceGenerator {

  def doGenerate(desc: String, messages: Seq[MessageWrapper]): String

}