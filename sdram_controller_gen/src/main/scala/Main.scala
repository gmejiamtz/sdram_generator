import play.api.libs.json._
object Hello {
  def main(args: Array[String]): Unit = {
    println(
      "This program is aimed at taking in a json config and spitting out Verilog for an SDRAM controller..."
    )
     if (args.length != 1) {
      println("Usage: sbt 'run <config_file_path>'")
      sys.exit(1)
     }
     val configFilePath = args(0)
    println(s"Config file path provided: $configFilePath")
    //Parse the config json file
    val jsonString = scala.io.Source.fromFile(configFilePath).mkString
    // Parse JSON
    val json = Json.parse(jsonString)
    // Extract config object
    val config = (json \ "config").as[JsObject]
    // Convert config object to Map[String, Int]
    val resultMap = config.value.collect {
      case (key, JsNumber(value)) => key -> value.toInt
    }.toMap
    //make this a optional verbose arg but static for now
    println("Configs")
    resultMap.foreach { case (key, value) =>
      println(s"$key -> $value")
    }
    
  }
}