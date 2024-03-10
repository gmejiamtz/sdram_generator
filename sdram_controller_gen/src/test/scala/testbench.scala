// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._
import scala.concurrent.duration._

class InitializationTest extends AnyFreeSpec with ChiselScalatestTester {
  "Tests for Initialization correctness" in {
    //wanted coded item 0000_0111_0000
    val burst_length = 0
    val burst_type = 0
    val cas_latency = 3
    val opcode = 0
    val write_burst = 0
    //should get address 0000_0011_0000 or 48 in decimal
    val params = new SDRAMControllerParams(16,12,1,1,burst_length,burst_type,cas_latency,opcode,write_burst)
    val init_cycle_time = (Duration(100, MICROSECONDS).toNanos.toInt /params.period.toFloat).ceil.toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
        dut.clock.setTimeout(0)
        for(cycle <- 0 until (init_cycle_time + 3)){
            dut.io.state_out.expect(ControllerState.initialization)
            if(cycle < init_cycle_time){
              //expect nops
              dut.io.sdram_control.cs.expect(false.B)
              dut.io.sdram_control.cas.expect(true.B)
              dut.io.sdram_control.ras.expect(true.B)
              dut.io.sdram_control.we.expect(true.B)
            } else if(cycle == (init_cycle_time)){
              //expect precharge
              dut.io.sdram_control.cs.expect(false.B)
              dut.io.sdram_control.ras.expect(false.B)
              dut.io.sdram_control.cas.expect(true.B)
              dut.io.sdram_control.we.expect(false.B) 
            } else if(cycle == (init_cycle_time + 1) || cycle == (init_cycle_time + 2)){
              //expect auto refresh
              dut.io.sdram_control.cs.expect(false.B)
              dut.io.sdram_control.ras.expect(false.B)
              dut.io.sdram_control.cas.expect(false.B)
              dut.io.sdram_control.we.expect(true.B) 
            }
            dut.clock.step()
        }
      //expect mode load
      dut.io.state_out.expect(ControllerState.initialization)
      dut.io.sdram_control.cs.expect(false.B)
      dut.io.sdram_control.ras.expect(false.B)
      dut.io.sdram_control.cas.expect(false.B)
      dut.io.sdram_control.we.expect(false.B)
      dut.io.sdram_control.address_bus.expect(48.U)
      dut.clock.step()
      //check if in idle
      dut.io.state_out.expect(ControllerState.idle)
    }
  }
}