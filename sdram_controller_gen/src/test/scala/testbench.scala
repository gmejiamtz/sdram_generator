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

class SDRAMControllerTestBench extends AnyFreeSpec with ChiselScalatestTester {
  def expectNOPs (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.we.expect(true.B) 
  }

  def expectPrecharge (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.we.expect(false.B) 
  }

  def expectRefresh (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(true.B)  
  }

  def expectModeLoad (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(false.B)
  } 

  def expectActive (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(false.B)
    dut.io.sdram_control.cas.expect(true.B)
    dut.io.sdram_control.we.expect(true.B) 
  }  

  def expectRead (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(true.B)  
  }

  def expectWrite (dut: SDRAMController): Unit = {
    dut.io.sdram_control.cs.expect(false.B)
    dut.io.sdram_control.ras.expect(true.B)
    dut.io.sdram_control.cas.expect(false.B)
    dut.io.sdram_control.we.expect(false.B)  
  }

  "Tests for Initialization correctness" in {
    //wanted coded item 0000_0111_0000
    val burst_length = 0
    val burst_type = 0
    val cas_latency = 3
    val opcode = 0
    val write_burst = 0
    val params = new SDRAMControllerParams(16,12,1,1,burst_length,burst_type,cas_latency,opcode,write_burst)
    val init_cycle_time = (Duration(100, MICROSECONDS).toNanos.toInt /params.period.toFloat).ceil.toInt
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
        dut.clock.setTimeout(0)
        for(cycle <- 0 until (init_cycle_time + 3)){
            dut.io.state_out.expect(ControllerState.initialization)
            if(cycle < init_cycle_time){
              //expect nops
              expectNOPs(dut)
            } else if(cycle == (init_cycle_time)){
              //expect precharge
              expectPrecharge(dut)
            } else if(cycle == (init_cycle_time + 1) || cycle == (init_cycle_time + 2)){
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

  "Test for read data validity" in {
    //wanted coded item 0000_0111_0000
    val burst_length = 0
    val burst_type = 0
    val cas_latency = 3
    val opcode = 0
    val write_burst = 0
    val params = new SDRAMControllerParams(16,12,1,1,burst_length,burst_type,cas_latency,opcode,write_burst)
    val init_cycle_time = (Duration(100, MICROSECONDS).toNanos.toInt /params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
        dut.clock.setTimeout(0)
        //let sdram initialize and program
        for(cycle <- 0 until (init_cycle_time + 3)){
            dut.io.state_out.expect(ControllerState.initialization)
            dut.clock.step()
        }
        dut.io.state_out.expect(ControllerState.initialization)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //ready for input
        dut.io.read_row_addresses(0).poke(0.U)
        dut.io.read_start(0).poke(true.B)
        //send active command
        expectActive(dut)
        dut.clock.step()
        dut.io.read_start(0).poke(false.B)
        //expect active state
        for(act_to_read <- 0 until active_to_rw_delay){
          dut.io.state_out.expect(ControllerState.active)
          //expect nops
          expectNOPs(dut)
          dut.clock.step()
        }
        //expect read command being sent and send in an address
        dut.io.read_col_addresses(0).poke(10.U)
        expectRead(dut) 
        dut.io.sdram_control.address_bus.expect(10.U)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.reading)
        //loop until cas latency is reached
        for(time_in_read <- 0 until cas_latency){
          dut.io.state_out.expect(ControllerState.reading) 
          dut.io.read_data_valid(0).expect(false.B)
          dut.clock.step()
        }
        //no clock step means this value is high on a transition
        dut.io.read_data_valid(0).expect(true.B)
        //expect precharge to end read
        expectPrecharge(dut)
        dut.clock.step()
        dut.io.state_out.expect(ControllerState.idle)
        //read done
    }
  }

  "Tests for write data validity" in {
   //wanted coded item 0000_0111_0000
    val burst_length = 0
    val burst_type = 0
    val cas_latency = 3
    val opcode = 0
    val write_burst = 0
    val params = new SDRAMControllerParams(16,12,1,1,burst_length,burst_type,cas_latency,opcode,write_burst)
    val init_cycle_time = (Duration(100, MICROSECONDS).toNanos.toInt /params.period.toFloat).ceil.toInt
    val active_to_rw_delay = params.active_to_rw_delay
    test(new SDRAMController(params)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
      dut.clock.setTimeout(0)
      //let sdram initialize and program
      for(cycle <- 0 until (init_cycle_time + 3)){
          dut.io.state_out.expect(ControllerState.initialization)
          dut.clock.step()
      }
      dut.io.state_out.expect(ControllerState.initialization)
      dut.clock.step()
      dut.io.state_out.expect(ControllerState.idle)
      //ready for input
      dut.io.write_row_addresses(45.U)
      dut.io.write_start(0).poke(true.B)
      //send active command
      expectActive(dut)
      dut.clock.step()
      dut.io.write_start(0).poke(false.B)
      //expect active state
      for(act_to_read <- 0 until active_to_rw_delay){
        dut.io.state_out.expect(ControllerState.active)
        //expect nops
        expectNOPs(dut)
        dut.clock.step()
      } 
      //expect write command being sent and send in an address
      dut.io.write_col_addresses(0).poke(28.U)
      expectWrite(dut) 
      dut.io.sdram_control.address_bus.expect(28.U)
      dut.clock.step()
      dut.io.state_out.expect(ControllerState.writing)
      //loop until cas latency is reached
      for(time_in_read <- 0 until params.t_rw_cycles){
        dut.io.state_out.expect(ControllerState.writing) 
        dut.io.read_data_valid(0).expect(false.B)
        dut.clock.step()
      }
      //no clock step means this value is high on a transition
      dut.io.write_data_valid(0).expect(true.B)
      //expect precharge to end read
      expectPrecharge(dut)
      dut.clock.step()
      dut.io.state_out.expect(ControllerState.idle)
      //write done 
    }
  }
}