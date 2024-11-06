import chisel3._
import chisel3.util._
import scala.concurrent.duration._
import chisel3.experimental.Analog
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource}

class SDRAMController(p: SDRAMControllerParams) extends Module {
  val io = IO(new SDRAMControllerIO(p))

  //initialization is a to be added feature for now just wait a cycles then go to idle
  val state = RegInit(ControllerState.initialization)
  //hold read data
  val read_data_reg = Reg(UInt(p.data_width.W))
  val stated_read = RegInit(false.B)
  val started_write = RegInit(false.B)
  val oen_reg = RegInit(false.B)
  //counter for read data being valid
  val cas_counter = Counter(p.cas_latency + 1)
  //counter to terminate write
  val terminate_write = Counter(p.t_rw_cycles + 1)

  //cycles to spam NOPs for SDRAM initialization
  val cycles_for_100us =
    (Duration(100, MICROSECONDS).toNanos.toInt / p.period.toFloat).ceil.toInt
  //the extra 3 cycles are for the 1 precharge and 2 auto refreshes need for programming SDRAM
  val hundred_micro_sec_counter = Counter(cycles_for_100us + 4)
  //active to read or write counter
  val active_to_rw_counter = Counter(p.active_to_rw_delay + 1)
  val refresh_every_cycles = (p.time_for_1_refresh.toInt / p.period.toFloat).ceil.toInt - 2

  // I tried to get this to work using just wrap but it asserted refresh every cycle
  // idk counters have just always been a bit bugged
  val refresh_counter = Counter(refresh_every_cycles)
  val refresh_outstanding = RegInit(false.B)
  when (refresh_counter.inc()) {
    refresh_outstanding := true.B
  }

  //Default SDRAM signals
  io.sdram_control.address_bus := DontCare
  //io.sdram_control.data_out_and_in := DontCare
  io.sdram_control.cs := DontCare
  io.sdram_control.ras := DontCare
  io.sdram_control.cas := DontCare
  io.sdram_control.we := DontCare
  //handle analog conntion
  val handle_analog = Module(new AnalogConnection(p))
  //io.sdram_control.data_out_and_in <> handle_analog.io.data_inout 
  //handle_analog.io.write_data := io.write_data(0)
  //handle_analog.io.oen := oen_reg
  //io.read_data(0) := handle_analog.io.read_data
  //other outputs
  io.state_out := state
  io.read_data_valid(0) := false.B
  io.write_data_valid(0) := false.B
  switch(state) {
    is(ControllerState.initialization) {
      //later each sdram will require its own init module
      //1. NOPs for 100us
      //2. A Precharge
      //3. 2 Auto Refresh
      //4. In Mode Programming mode
      //nop command for 100us
      sendNop()
      hundred_micro_sec_counter.inc()
      //time to precharge
      when(hundred_micro_sec_counter.value === cycles_for_100us.U) {
        sendPrecharge()
      }.elsewhen(
          (hundred_micro_sec_counter.value === (cycles_for_100us + 1).U) | (hundred_micro_sec_counter.value === (cycles_for_100us + 2).U)
        ) {
          //time to auto refresh
          sendRefresh()
          refresh_outstanding := false.B
        }
        .elsewhen(hundred_micro_sec_counter.value === (cycles_for_100us + 3).U) {
          //time to program
          sendModeProg()
          //address holds programmed options
          //12'b00_wb_opcode_cas_bT_bL
          io.sdram_control.address_bus := Cat(
            0.U(2.W),
            p.write_burst.U(1.W),
            p.opcode.U(2.W),
            p.cas_latency.U(3.W),
            p.burst_type.U(1.W),
            p.burst_length.U(3.W)
          )
          hundred_micro_sec_counter.reset()
          state := ControllerState.idle
        }
    }
    is(ControllerState.idle) {
      state := ControllerState.idle
      //address holds row right now
      val go_to_active =
        io.read_start.exists(identity) | io.write_start.exists(identity)
      //nop command
      sendNop()
      when (refresh_outstanding) {
        sendRefresh()
      } .elsewhen(go_to_active) {
        state := ControllerState.active
        //active command - make this a function
        sendActive()
        when(io.read_start.exists(identity)) {
          stated_read := true.B
          cas_counter.reset()
          io.sdram_control.address_bus := io.read_row_addresses(0)
        }.elsewhen(io.write_start.exists(identity)) {
          started_write := true.B
          io.sdram_control.address_bus := io.write_row_addresses(0)
        }
      }
    }
    is(ControllerState.active) {
      state := ControllerState.active
      //read priority for now
      val we_are_reading = stated_read
      val we_are_writing = started_write
      active_to_rw_counter.inc()
      //nop command
      sendNop()
      //address bus needs to hold row address
      io.sdram_control.address_bus := io.read_row_addresses(0)
      when(
        we_are_reading & active_to_rw_counter.value === (p.active_to_rw_delay.U)
      ) {
        state := ControllerState.reading
        //read command
        sendRead()
        oen_reg := true.B
        stated_read := false.B
        //address bus now holds col address
        io.sdram_control.address_bus := io.read_col_addresses(0)
        cas_counter.inc()
      }.elsewhen(
        we_are_writing & active_to_rw_counter.value === (p.active_to_rw_delay.U)
      ) {
        state := ControllerState.writing
        //write command
        started_write := false.B
        oen_reg := false.B
        sendWrite()
        //address bus now holds col address
        io.sdram_control.address_bus := io.write_col_addresses(0)
      } .elsewhen (refresh_outstanding) {
        sendRefresh()
        refresh_outstanding := false.B

      }
    }
    is(ControllerState.reading) {
      state := ControllerState.reading
      cas_counter.inc()
      //nop command
      sendNop()
      when(cas_counter.value === p.cas_latency.U) {
        //data is valid
        io.read_data_valid(0) := true.B
        //precharge command
        //io.read_data := read_data_reg
        sendPrecharge()
        state := ControllerState.idle
      }
    }
    is(ControllerState.writing) {
      terminate_write.inc()
      //send nops
      sendNop()
      when(terminate_write.value === p.t_rw_cycles.U) {
        //precharge command
        sendPrecharge()
        io.write_data_valid(0) := true.B
        state := ControllerState.idle
      }
    }
  }
}