import chisel3._
import chisel3.util._
import scala.concurrent.duration._
import chisel3.experimental.Analog
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource}

object ControllerState extends ChiselEnum {
  val clearing, initialization, idle, active, refresh, nop1, reading, writing,
    precharge, nop2 = Value
}

case class SDRAMControllerParams(
  datasheet: Map[String, Int]
) {
  //anything set to a concrete number is likely to be replaced with a parameter
  require(
    datasheet("num_of_read_channels") > 0,
    "Number of read channels is not greater than 0"
  )
  require(
    datasheet("num_of_write_channels")  > 0,
    "Number of write channels is not greater than 0"
  )
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
  require(datasheet("opcode") == 0, "Opcode must be 0, all other values reserved")
  require(
    datasheet("write_burst") == 0 || datasheet("write_burst") == 1,
    "Write Burst must be 0 for Programmed Length or 1 for single location access"
  )

  val read_channels = datasheet("num_of_read_channels")
  val write_channels = datasheet("num_of_write_channels")
  val data_width = datasheet("data_width")
  val address_width = datasheet("address_width")
  //set by user
  val burst_length = datasheet("burst_length")
  val burst_type = datasheet("burst_length")
  val cas_latency = datasheet("cas_latency")
  val opcode = datasheet("opcode")
  val write_burst = datasheet("write_burst")
  //set by user
  val frequency = datasheet("frequency") * datasheet("frequency_scale") //MHz
  //get duration of a single period in ns
  val period_duration = Duration(1 / frequency.toFloat, SECONDS)
  val period = period_duration.toNanos.toInt
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
  //address to index row and col - shared in sdram
  val address_bus = Output(UInt(p.address_width.W))
  //when reading its output when writing it is input
  //val data_out_and_in = Analog((p.data_width.W))
}

class SDRAMControllerIO(p: SDRAMControllerParams) extends Bundle {
  //require at least one read and one write channel
  require(p.read_channels > 0)
  require(p.write_channels > 0)

  //read channels - for now vec is a vec of 1
  val read_row_addresses = Input(UInt(p.address_width.W))

  val read_col_addresses = Input(UInt(p.address_width.W))
  //data movement too hard due to bidirectional data TBA - focus on requests
  val read_data = Output(UInt(p.data_width.W))
  val read_data_valid = Output(Bool())
  //read start
  val read_start = Input(Bool()))

  //write channels
  val write_row_addresses = Input(UInt(p.address_width.W))
  val write_col_addresses = Input(UInt(p.address_width.W))
  val write_data = Input(UInt(p.data_width.W))
  val write_data_valid = Output(Bool())
  val write_start = Input(Bool())
  //wired to the actual sdram
  val sdram_control = new ToSDRAM(p)
  //debug purposes
  val state_out = Output(ControllerState())
}

class SDRAMCommands(parameters: SDRAMControllerParams, controls: ToSDRAM){
  var control = controls
  //functions to send commands
  def sendNop(): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := true.B
    control.we := true.B
  }

  def sendPrecharge(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := true.B
    control.we := false.B
  }

  def sendRefresh(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := false.B
    control.we := true.B
  }

  def sendModeProg(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := false.B
    control.we := false.B
  }

  def sendActive(): Unit = {
    control.cs := false.B
    control.ras := false.B
    control.cas := true.B
    control.we := true.B
  }

  def sendRead(): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := false.B
    control.we := true.B
  }

  def sendWrite(): Unit = {
    control.cs := false.B
    control.ras := true.B
    control.cas := false.B
    control.we := false.B
  }
}