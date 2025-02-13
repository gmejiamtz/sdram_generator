package sdram_general

import chisel3._
import chisel3.util._
import scala.concurrent.duration._
import chisel3.experimental.Analog
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource}
import scala.math.pow

object ControllerState extends ChiselEnum {

  val clearing, initialization, idle, active, refresh, nop1, reading, writing,
    precharge, nop2 = Value
}

case class SDRAMControllerParams(
  datasheet: Map[String, Int],
  self_refresh: Boolean
) {
  //anything set to a concrete number is likely to be replaced with a parameter
  require(
    datasheet("burst_length") >= 0 && datasheet("burst_length") <= 3,
    "Burst length must be between 0 and 3"
  )
  require(
    datasheet("burst_type") == 0 | datasheet("burst_type") == 1,
    "Burst Type must be 0 for Sequential or 1 for Interleaved"
  )
  require(
    datasheet("cas_latency") >= 1 | datasheet("burst_length") <= 3,
    "CAS Latency must between 1 and 3"
  )
  require(
    datasheet("opcode") == 0,
    "Opcode must be 0, all other values reserved"
  )
  require(
    datasheet("write_burst") == 0 || datasheet("write_burst") == 1,
    "Write Burst must be 0 for Programmed Length or 1 for single location access"
  )
  require(datasheet("dqm_width") > 0, "DQM must be given as 1 or higher")

  val data_width = datasheet("data_width")
  val address_width = datasheet("address_width")
  val dqm_width = datasheet("dqm_width")
  //set by user
  val burst_length = datasheet("burst_length")
  val burst_type = datasheet("burst_type")
  val cas_latency = datasheet("cas_latency")
  val opcode = datasheet("opcode")
  val write_burst = datasheet("write_burst")
  //set by user
  val frequency = datasheet("frequency") * datasheet("frequency_scale") //MHz
  //get duration of a single period in ns
  val period_duration = Duration(1 / frequency.toFloat, SECONDS)
  val period = period_duration.toNanos.toInt
  val auto_refresh: Boolean = self_refresh

  //cycles to spam NOPs for SDRAM initialization
  val cycles_for_100us =
    (Duration(100, MICROSECONDS).toNanos.toInt / period.toFloat).ceil.toInt
  //needs to change to variable = ceil(t_rcd / clk_period)
  val active_to_rw_delay = (datasheet("t_rcd").toFloat / period).ceil.toInt
  //hardcoded by the datasheet - converted to ns
  val t_ref = datasheet("t_ref")
  val time_for_1_refresh = Duration(t_ref.toFloat / 2048, MILLISECONDS).toNanos
  //10 is hardcoded by the lab doc - assume no auto precharge
  val t_rw_cycles = (datasheet("t_wr") / period.toFloat).ceil.toInt
}

class ToSDRAM(p: SDRAMControllerParams) extends Bundle {
  //commands
  val cs = Output(Bool())
  val ras = Output(Bool())
  val cas = Output(Bool())
  val we = Output(Bool())
  val dqm = Output(UInt(p.dqm_width.W))
  //address to index row and col - shared in sdram
  val address_bus = Output(UInt(p.address_width.W))
  //when reading its output when writing it is input
  val dq = Analog((p.data_width.W))
}

class SDRAMControllerIO(p: SDRAMControllerParams) extends Bundle {
  val read_row_address = Input(UInt(p.address_width.W))
  val read_col_address = Input(UInt(p.address_width.W))
  val read_data = Output(UInt(p.data_width.W))
  val read_data_valid = Output(Bool())
  val read_start = Input(Bool())
  //write channels
  val write_row_address = Input(UInt(p.address_width.W))
  val write_col_address = Input(UInt(p.address_width.W))
  val write_data = Input(UInt(p.data_width.W))
  val write_data_valid = Output(Bool())
  val write_start = Input(Bool())
  //wired to the actual sdram
  val sdram_control = new ToSDRAM(p)
  //debug purposes
  val state_out = Output(ControllerState())
}

class SDRAMCommands(parameters: SDRAMControllerParams, controls: ToSDRAM) {
  var control = controls

  def initialize_controls(): Unit = {
    //Default SDRAM signals
    control.address_bus := DontCare
    control.dq := DontCare
    control.cs := DontCare
    control.ras := DontCare
    control.cas := DontCare
    control.we := DontCare
    control.dqm := DontCare
  }

  //functions to send commands
  def NOP(): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := true.B
    control.we := true.B
    control.address_bus := DontCare
    control.dqm := 0.U(2.W)
  }

  def Precharge(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := true.B
    control.we := false.B
    control.address_bus := DontCare
    control.dqm := 0.U(2.W)

  }

  def Refresh(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := false.B
    control.we := true.B
    control.address_bus := DontCare
    control.dqm := 0.U(2.W)

  }

  def Active(row_and_bank: UInt): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := true.B
    control.we := true.B
    control.address_bus := row_and_bank
    control.dqm := 3.U(2.W)

  }

  def Program_Mode_Reg(wrB_wrM_opcode_cas_bT_bL: UInt): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := false.B
    control.we := false.B
    control.address_bus := wrB_wrM_opcode_cas_bT_bL
    control.dqm := 0.U(2.W)
  }

  def Read(column: UInt): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := false.B
    control.we := true.B
    control.address_bus := column
    control.dqm := 3.U(2.W)
  }

  def Write(column: UInt): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := false.B
    control.we := false.B
    control.address_bus := column
    control.dqm := 3.U(2.W)
  }
}

class AnalogConnection(p: SDRAMControllerParams) extends BlackBox with HasBlackBoxInline {
    val io = IO(new Bundle {
    val data_inout = Analog(p.data_width.W)
    val read_data = Output(UInt(p.data_width.W))
    val write_data = Input(UInt(p.data_width.W))
    val oen = Input(Bool())
  })

  setInline("AnalogConnection.sv",
    s"""
    |module AnalogConnection(
    |     inout [${p.data_width - 1}:0] data_inout,
    |     output [${p.data_width - 1}:0] read_data,
    |     input [${p.data_width - 1}:0] write_data,
    |     input oen);
    |
    |   assign data_inout = (oen == 'b0) ? write_data : 'bz;
    |   assign read_data = data_inout;
    |endmodule
    """.stripMargin)
}

