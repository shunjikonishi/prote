package testgen

object SourceGeneratorRegistry {

  private val map: Map[String, (String, SourceGenerator)] = Map(
    "mocha" -> ("JavaScript(Mocha)", new MochaTestGenerator)
  )

  val emptyGenerator = new EmptyTestGenerator()

  def get(name: String) = map.get(name).map(_._2).getOrElse(emptyGenerator)

  def generators: Map[String, String] = map.mapValues(_._1)
}