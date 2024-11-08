import play.api.libs.json._
import chisel3.stage.ChiselStage
import sdram_general._
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
    //test for generation
    //wanted coded item 0000_0111_0000
    val burst_length = 0
    val burst_type = 0
    val cas_latency = 3
    val opcode = 0
    val write_burst = 0
    val params = new SDRAMControllerParams(resultMap)
    val chiselStage = new ChiselStage
    //called SDRAMController.v
    chiselStage.emitVerilog(new SDRAMController(params), args)
    println("Verilog Generated at SDRAMController.v")
  }
}