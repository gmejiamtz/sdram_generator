import sdram_general._
import chisel3._
import chisel3.util._
import scala.concurrent.duration._
import chisel3.experimental.Analog
import chisel3.util.{HasBlackBoxInline, HasBlackBoxResource}

class SDRAMController(p: SDRAMControllerParams) extends Module {
  val io = IO(new SDRAMControllerIO(p))
  val sdram_commands = new SDRAMCommands(p, io.sdram_control)
  sdram_commands.initialize_controls()
  //initialization is a to be added feature for now just wait a cycles then go to idle
  val state = RegInit(ControllerState.initialization)
  //registers
  val read_data_reg = Reg(UInt(p.data_width.W))
  val started_read = RegInit(false.B)
  val started_write = RegInit(false.B)
  val oen_reg = RegInit(false.B)
  val refresh_outstanding = RegInit(false.B)
  //counters
  //counter for read data being valid
  val cas_counter = Counter(p.cas_latency + 1)
  //the extra 3 cycles are for the 1 precharge and 2 auto refreshes need for programming SDRAM
  val hundred_micro_sec_counter = Counter(p.cycles_for_100us + 4)
  //active to read or write counter
  val active_to_rw_counter = Counter(p.active_to_rw_delay + 1)

  val read_state_counter = Counter(
    p.cas_latency + scala.math.pow(2, p.burst_length).toInt + 1
  )
  val write_state_counter = Counter(scala.math.pow(2, p.burst_length).toInt + 1)

  val refresh_every_cycles =
    (p.time_for_1_refresh.toInt / p.period.toFloat).ceil.toInt - 2

  // I tried to get this to work using just wrap but it asserted refresh every cycle
  // idk counters have just always been a bit bugged
  val auto_refresh = true

  val refresh_counter: Option[Counter] =
    if (auto_refresh) Some(Counter(refresh_every_cycles)) else None
  if (refresh_counter.isDefined) {
    when((refresh_counter.get.value === (refresh_every_cycles - 1).U)) {
      refresh_outstanding := true.B
      refresh_counter.get.reset()
    }.otherwise {
      refresh_counter.get.inc()
    }
  }
  /*
  //handle analog conntion
  val handle_analog = Module(new AnalogConnection(p))
  io.sdram_control.dq <> handle_analog.io.data_inout
  handle_analog.io.write_data := io.write_data
  handle_analog.io.oen := oen_reg
  io.read_data := handle_analog.io.read_data
   */
  io.read_data := DontCare //defaults to this since analog handles data movement
  //other outputs
  io.state_out := state
  io.read_data_valid := false.B
  io.write_data_valid := false.B
  switch(state) {
    is(ControllerState.initialization) {
      //later each sdram will require its own init module
      //1. NOPs for 100us
      //2. A Precharge
      //3. 2 Auto Refresh
      //4. In Mode Programming mode
      //nop command for 100us
      sdram_commands.NOP()
      hundred_micro_sec_counter.inc()
      //time to precharge
      when(hundred_micro_sec_counter.value === p.cycles_for_100us.U) {
        sdram_commands.Precharge()
      }.elsewhen(
          (hundred_micro_sec_counter.value === (p.cycles_for_100us + 1).U) | (hundred_micro_sec_counter.value === (p.cycles_for_100us + 2).U)
        ) {
          //time to auto refresh
          sdram_commands.Refresh()
          refresh_outstanding := false.B
        }
        .elsewhen(
          hundred_micro_sec_counter.value === (p.cycles_for_100us + 3).U
        ) {
          //time to program
          //address holds programmed options
          //12'b00_wb_opcode_cas_bT_bL
          val mode = Cat(
            0.U(2.W),
            p.write_burst.U(1.W),
            p.opcode.U(2.W),
            p.cas_latency.U(3.W),
            p.burst_type.U(1.W),
            p.burst_length.U(3.W)
          )
          sdram_commands.Program_Mode_Reg(mode)
          hundred_micro_sec_counter.reset()
          state := ControllerState.idle
        }
    }
    is(ControllerState.idle) {
      state := ControllerState.idle
      //address holds row right now
      read_state_counter.reset()
      write_state_counter.reset()
      val go_to_active =
        io.read_start | io.write_start
      //nop command
      sdram_commands.NOP()
      //read and write isnt valid
      io.read_data_valid := false.B
      io.write_data_valid := false.B
      if (refresh_counter.isDefined) {
        when(refresh_outstanding) {
          sdram_commands.Refresh()
          refresh_outstanding := false.B
        }
      }
      when(go_to_active) {
        state := ControllerState.active
        //active command - make this a function
        val row_and_bank = io.read_row_address
        sdram_commands.Active(row_and_bank)
        cas_counter.reset()
        when(io.read_start) {
          started_read := true.B
        }.elsewhen(io.write_start) {
          started_write := true.B
        }
      }
    }
    is(ControllerState.active) {
      state := ControllerState.active
      //read priority for now
      val we_are_reading = started_read
      val we_are_writing = started_write
      active_to_rw_counter.inc()
      //nop command
      sdram_commands.NOP()
      when(
        we_are_reading & active_to_rw_counter.value === (p.active_to_rw_delay.U)
      ) {
        state := ControllerState.reading
        //read command
        val column = io.read_col_address
        active_to_rw_counter.reset()
        sdram_commands.Read(column)
        oen_reg := true.B
        started_read := false.B
        //address bus now holds col address
        io.sdram_control.address_bus := io.read_col_address
        cas_counter.inc()
        read_state_counter.inc()
      }.elsewhen(
        we_are_writing & active_to_rw_counter.value === (p.active_to_rw_delay.U)
      ) {
        state := ControllerState.writing
        //write command
        val column = io.write_col_address
        active_to_rw_counter.reset()
        started_write := false.B
        oen_reg := false.B
        sdram_commands.Write(column)
        //address bus now holds col address
        io.sdram_control.address_bus := io.write_col_address
        io.write_data_valid := true.B
        write_state_counter.inc()
      }
      if (refresh_counter.isDefined) {
        when(refresh_outstanding) {
          sdram_commands.Refresh()
          refresh_outstanding := false.B
        }
      }
    }
    is(ControllerState.reading) {
      state := ControllerState.reading
      //nop command
      sdram_commands.NOP()
      read_state_counter.inc()
      when(~(cas_counter.value === p.cas_latency.U)) {
        cas_counter.inc()
      }
      //once cas latency reached data is valid until the burst ends
      when(cas_counter.value === p.cas_latency.U) {
        //data is valid
        io.read_data_valid := true.B
        //io.read_data := read_data_reg
      }
      when(
        read_state_counter.value === (p.cas_latency + scala.math
          .pow(2, p.burst_length)
          .toInt).U
      ) {
        state := ControllerState.idle
        sdram_commands.Precharge()
      }
    }
    is(ControllerState.writing) {
      //send nops
      sdram_commands.NOP()
      io.write_data_valid := true.B
      write_state_counter.inc()
      when(
        write_state_counter.value === scala.math.pow(2, p.burst_length).toInt.U
      ) {
        //precharge command
        sdram_commands.Precharge()
        state := ControllerState.idle
      }
    }
  }
}
