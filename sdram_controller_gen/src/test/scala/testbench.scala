// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._
import scala.concurrent.duration._
import os.truncate
import java.util.ResourceBundle.Control
import java.lang.ModuleLayer.Controller
import play.api.libs.json._
import sdram_general._

class SDRAMControllerTestBench extends AnyFreeSpec with ChiselScalatestTester {

  def expectNOPs(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.we.expect(true.B)
  }

  def expectPrecharge(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.we.expect(false.B)
  }

  def expectRefresh(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(true.B)
  }

  def expectModeLoad(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(false.B)
  }

  def expectActive(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.we.expect(true.B)
  }

  def expectRead(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(true.B)
  }

  def expectWrite(dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(false.B)
  }

  def create_datasheet_map(path_to_file: String): Map[String, Int] = {
    val jsonString = scala.io.Source.fromFile(path_to_file).mkString
    // Parse JSON
    val json = Json.parse(jsonString)
    // Extract config object
    val config = (json \ "config").as[JsObject]
    // Convert config object to Map[String, Int]
    val resultMap = config.value.collect {
      case (key, JsNumber(value)) => key -> value.toInt
    }.toMap
    resultMap
  }

  def self_refresh_mode(path_to_file: String): Boolean = {
    val jsonString = scala.io.Source.fromFile(path_to_file).mkString
    // Parse JSON
    val json = Json.parse(jsonString)
    // Extract config object
    val config = (json \ "config").as[JsObject]
    val self_refresh: Boolean =
      (json \ "self-refresh").asOpt[Boolean].getOrElse(true)
    self_refresh
  }

  "Tests for Initialization correctness" in {
    //wanted coded item 0000_0111_0000
    val path_to_test_config = "../templates/test_templates/cas_latency3.json"
    val datasheet = create_datasheet_map(path_to_test_config)
    val self_refresh = self_refresh_mode(path_to_test_config)
    val params = new SDRAMControllerParams(datasheet, self_refresh)
    val init_cycle_time =
      (Duration(100, MICROSECONDS).toNanos.toInt / params.period.toFloat).ceil.toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(0)
        for (cycle <- 0 until (init_cycle_time + 3)) {
          dut.io.state_out.expect(ControllerState.initialization)
          if (cycle < init_cycle_time) {
            //expect nops
            expectNOPs(dut)
          } else if (cycle == (init_cycle_time)) {
            //expect precharge
            expectPrecharge(dut)
          } else if (cycle == (init_cycle_time + 1) || cycle == (init_cycle_time + 2)) {
            //expect auto refresh
            expectRefresh(dut)
          }
          dut.clock.step()
        }
        //expect mode load
        dut.io.state_out.expect(ControllerState.initialization)
        expectModeLoad(dut)
        dut.io.sdram_control.address_bus.expect(48.U)
        dut.clock.step()
        //check if in idle
        dut.io.state_out.expect(ControllerState.idle)
    }
  }

  "Tests for read data validity with cas latency of 3" in {
    //wanted coded item 0000_0111_0000
    val path_to_test_config = "../templates/test_templates/cas_latency3.json"
    val datasheet = create_datasheet_map(path_to_test_config)
    val self_refresh = self_refresh_mode(path_to_test_config)
    val params = new SDRAMControllerParams(datasheet, self_refresh)
    val init_cycle_time =
      (Duration(100, MICROSECONDS).toNanos.toInt / params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    val read_time =
      (params.cas_latency + scala.math.pow(2, params.burst_length)).toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(0)
        //let sdram initialize and program
        for (cycle <- 0 until (init_cycle_time + 3)) {
          dut.io.state_out.expect(ControllerState.initialization)
          dut.clock.step()
        }
        dut.io.state_out.expect(ControllerState.initialization)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //ready for input
        dut.io.read_row_address.poke(0.U)
        dut.io.read_start.poke(true.B)
        //send active command
        expectActive(dut)
        dut.clock.step()
        dut.io.read_start.poke(false.B)
        //expect active state
        for (act_to_read <- 0 until active_to_rw_delay) {
          dut.io.state_out.expect(ControllerState.active)
          //expect nops
          expectNOPs(dut)
          dut.clock.step()
        }
        //expect read command being sent and send in an address
        dut.io.read_col_address.poke(10.U)
        expectRead(dut)
        dut.io.sdram_control.address_bus.expect(10.U)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.reading)
        //loop until cas latency is reached
        for (time_in_read <- 1 until (read_time)) {
          dut.io.state_out.expect(ControllerState.reading)
          if (time_in_read >= params.cas_latency) {
            dut.io.read_data_valid.expect(true.B)
          } else {
            dut.io.read_data_valid.expect(false.B)
          }
          dut.clock.step()
        }
        //no clock step means this value is high on a transition
        dut.io.read_data_valid.expect(true.B)
        //expect precharge to end read
        expectPrecharge(dut)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
      //read done
    }
  }

  "Tests for read data validity with cas latency of 2" in {
    //wanted coded item 0000_0111_0000
    val path_to_test_config = "../templates/test_templates/cas_latency2.json"
    val datasheet = create_datasheet_map(path_to_test_config)
    val self_refresh = self_refresh_mode(path_to_test_config)
    val params = new SDRAMControllerParams(datasheet, self_refresh)
    val init_cycle_time =
      (Duration(100, MICROSECONDS).toNanos.toInt / params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    val read_time =
      (params.cas_latency + scala.math.pow(2, params.burst_length)).toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(0)
        //let sdram initialize and program
        for (cycle <- 0 until (init_cycle_time + 3)) {
          dut.io.state_out.expect(ControllerState.initialization)
          dut.clock.step()
        }
        dut.io.state_out.expect(ControllerState.initialization)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //ready for input
        dut.io.read_row_address.poke(0.U)
        dut.io.read_start.poke(true.B)
        //send active command
        expectActive(dut)
        dut.clock.step()
        dut.io.read_start.poke(false.B)
        //expect active state
        for (act_to_read <- 0 until active_to_rw_delay) {
          dut.io.state_out.expect(ControllerState.active)
          //expect nops
          expectNOPs(dut)
          dut.clock.step()
        }
        //expect read command being sent and send in an address
        dut.io.read_col_address.poke(10.U)
        expectRead(dut)
        dut.io.sdram_control.address_bus.expect(10.U)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.reading)
        //loop until cas latency is reached
        for (time_in_read <- 1 until (read_time)) {
          dut.io.state_out.expect(ControllerState.reading)
          if (time_in_read >= params.cas_latency) {
            dut.io.read_data_valid.expect(true.B)
          } else {
            dut.io.read_data_valid.expect(false.B)
          }
          dut.clock.step()
        }
        //no clock step means this value is high on a transition
        dut.io.read_data_valid.expect(true.B)
        //expect precharge to end read
        expectPrecharge(dut)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
      //read done
    }
  }

  "Tests for read data validity with cas latency of 1" in {
    //wanted coded item 0000_0111_0000
    val path_to_test_config = "../templates/test_templates/cas_latency2.json"
    val datasheet = create_datasheet_map(path_to_test_config)
    val self_refresh = self_refresh_mode(path_to_test_config)
    val params = new SDRAMControllerParams(datasheet, self_refresh)
    val init_cycle_time =
      (Duration(100, MICROSECONDS).toNanos.toInt / params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    val read_time =
      (params.cas_latency + scala.math.pow(2, params.burst_length)).toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(0)
        //let sdram initialize and program
        for (cycle <- 0 until (init_cycle_time + 3)) {
          dut.io.state_out.expect(ControllerState.initialization)
          dut.clock.step()
        }
        dut.io.state_out.expect(ControllerState.initialization)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //ready for input
        dut.io.read_row_address.poke(0.U)
        dut.io.read_start.poke(true.B)
        //send active command
        expectActive(dut)
        dut.clock.step()
        dut.io.read_start.poke(false.B)
        //expect active state
        for (act_to_read <- 0 until active_to_rw_delay) {
          dut.io.state_out.expect(ControllerState.active)
          //expect nops
          expectNOPs(dut)
          dut.clock.step()
        }
        //expect read command being sent and send in an address
        dut.io.read_col_address.poke(10.U)
        expectRead(dut)
        dut.io.sdram_control.address_bus.expect(10.U)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.reading)
        //loop until cas latency is reached
        for (time_in_read <- 1 until (read_time)) {
          dut.io.state_out.expect(ControllerState.reading)
          if (time_in_read >= params.cas_latency) {
            dut.io.read_data_valid.expect(true.B)
          } else {
            dut.io.read_data_valid.expect(false.B)
          }
          dut.clock.step()
        }
        //no clock step means this value is high on a transition
        dut.io.read_data_valid.expect(true.B)
        //expect precharge to end read
        expectPrecharge(dut)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
      //read done
    }
  }

  "Tests for write data validity" in {
    //wanted coded item 0000_0111_0000
    val path_to_test_config = "../templates/test_templates/cas_latency3.json"
    val datasheet = create_datasheet_map(path_to_test_config)
    val self_refresh = self_refresh_mode(path_to_test_config)
    val params = new SDRAMControllerParams(datasheet, self_refresh)
    val init_cycle_time =
      (Duration(100, MICROSECONDS).toNanos.toInt / params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    val write_time = scala.math.pow(2, params.burst_length).toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {
      dut =>
        dut.clock.setTimeout(0)
        //let sdram initialize and program
        for (cycle <- 0 until (init_cycle_time + 3)) {
          dut.io.state_out.expect(ControllerState.initialization)
          dut.clock.step()
        }
        dut.io.state_out.expect(ControllerState.initialization)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //ready for input
        dut.io.write_row_address.poke(45.U)
        dut.io.write_start.poke(true.B)
        //send active command
        expectActive(dut)
        dut.clock.step()
        dut.io.write_start.poke(false.B)
        //expect active state
        for (act_to_read <- 0 until active_to_rw_delay) {
          dut.io.state_out.expect(ControllerState.active)
          //expect nops
          expectNOPs(dut)
          dut.clock.step()
        }
        //expect write command being sent and send in an address
        dut.io.write_col_address.poke(28.U)
        expectWrite(dut)
        dut.io.sdram_control.address_bus.expect(28.U)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.writing)
        //loop until cas latency is reached
        for (time_in_write <- 1 until write_time) {
          dut.io.state_out.expect(ControllerState.writing)
          dut.io.write_data_valid.expect(true.B)
          dut.clock.step()
        }
        //no clock step means this value is high on a transition
        dut.io.write_data_valid.expect(true.B)
        //expect precharge to end read
        expectPrecharge(dut)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
      //write done
    }
  }

}
