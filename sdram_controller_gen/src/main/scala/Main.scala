import play.api.libs.json._
import java.io.File
import scala.sys.process._
import chisel3.stage.ChiselStage
import sdram_general._
import sva._

object SDRAMController_Generate {

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
    resultMap.foreach {
      case (key, value) =>
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
    val curr_dir = System.getProperty("user.dir")
    //val formal_verify = true;
    val rm_formal_proc = Process(s"rm -rf $curr_dir/src/test/formal")
    rm_formal_proc.!
    val sdram_sv_name = "SDRAMController.sv"
    // Rewrite all modules to SV and add asserts to SDRAM Controller module
    new File("SDRAMController.v").renameTo(new File(sdram_sv_name))
    val sva_mods = new SVA_Modifier(s"$curr_dir/$sdram_sv_name", params)
    sva_mods.begin_formal_block()
    sva_mods.init_to_idle_assertion()
    sva_mods.idle_to_active_assertion()
    sva_mods.active_to_rw_assertion()
    sva_mods.never_reaches_init_after_reset_assert()
    sva_mods.end_formal_block()
    println(s"Verilog Generated at: $curr_dir")
    //val sby_proc = Process(s"sby $curr_dir/src/test/formal.sby")
    //sby_proc.!
  }
}
