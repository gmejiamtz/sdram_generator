import chisel3._
import chisel3.util._
import scala.concurrent.duration._
import chisel3.experimental.Analog

object ControllerState extends ChiselEnum {
    val clearing, initialization, idle, active, refresh, nop1, reading, writing, precharge, nop2 = Value
}

case class SDRAMControllerParams(
        data_w: Int,addr_w: Int, num_read_channels: Int, num_write_channels: Int,
        wanted_burst_length : Int, wanted_burst_type: Int, wanted_cas_latency: Int,
        wanted_op_code: Int, wanted_write_burst: Int){
    //anything set to a concrete number is likely to be replaced with a parameter
    require(num_read_channels > 0, "Number of read channels is not greater than 0")
    require(num_write_channels > 0, "Number of write channels is not greater than 0")
    require(wanted_burst_length >= 0 && wanted_burst_length <= 3, "Burst length must be between 0 and 3")
    require(wanted_burst_type == 0 | wanted_burst_type == 1, "Burst Type must be 0 for Sequential or 1 for Interleaved")
    require(wanted_cas_latency >= 1 | wanted_burst_length <= 3, "CAS Latency must between 1 and 3")
    require(wanted_op_code == 0, "Opcode must be 0, all other values reserved")
    require(wanted_write_burst == 0 || wanted_write_burst == 1, "Write Burst must be 0 for Programmed Length or 1 for single location access")

    val read_channels = num_read_channels
    val write_channels = num_write_channels
    val data_width = data_w
    val address_width = addr_w
    //set by user
    val burst_length = wanted_burst_length.U(3.W)
    val burst_type = wanted_burst_type.U(1.W)
    val cas_latency = wanted_cas_latency.U(3.W)
    val opcode = wanted_op_code.U(2.W)
    val write_burst = wanted_write_burst.U(1.W)
    //needs to change to variable = ceil(t_rcd / clk_period)
    val active_to_rw_delay = 3
    //set by user
    val frequency = 125000000 //MHz
    //get duration of a single period in ns
    val period_duration = Duration(1/frequency.toFloat, SECONDS)
    val period = period_duration.toNanos.toInt
    //hardcoded by the datasheet - converted to ns
    val t_ref = 64
    val time_for_1_refresh = Duration(t_ref.toFloat / 2048, SECONDS).toNanos
    //10 is hardcoded by the lab doc - assume no auto precharge
    val t_rw_cycles = (10/period.toFloat).ceil.toInt
}

class ToSDRAM(p: SDRAMControllerParams) extends Bundle{
    //commands
    val cs  = Output(Bool())
    val ras = Output(Bool())
    val cas = Output(Bool())
    val we  = Output(Bool()) 
    //address to index row and col - shared in sdram
    val address_bus = Output(UInt(p.address_width.W))
    //when reading its output when writing it is input
    //val data_out_and_in = Analog((p.data_width.W))
}

class SDRAMControllerIO(p: SDRAMControllerParams) extends Bundle {
    //require at least one read and one write channel
    require(p.num_read_channels > 0)
    require(p.num_write_channels > 0)
    //read channels - for now vec is a vec of 1
    val read_row_addresses = Input(Vec(p.num_read_channels, UInt(p.address_width.W)))
    val read_col_addresses = Input(Vec(p.num_read_channels, UInt(p.address_width.W)))
    //data movement too hard due to bidirectional data TBA - focus on requests 
    //val read_data = Vec(p.num_read_channels, Analog(p.data_width.W))
    val read_data_valid = Vec(p.num_read_channels, Bool()) 
    //read start 
    val read_start = Input(Vec(p.num_read_channels, Bool()))
    //write channels
    val write_row_addresses = Input(Vec(p.num_read_channels, UInt(p.address_width.W)))
    val write_col_addresses = Input(Vec(p.num_read_channels, UInt(p.address_width.W)))
    //val write_data = Vec(p.num_read_channels, Analog(p.data_width.W))
    val write_data_valid = Vec(p.num_read_channels, Bool()) 
    val write_start = Input(Vec(p.num_read_channels, Bool()))
    //wired to the actual sdram
    val sdram_control = new ToSDRAM(p)
    //debug purposes
    val state_out = Output(ControllerState())
}

class SDRAMController(p: SDRAMControllerParams) extends Module{
    val io = IO(new SDRAMControllerIO(p))
    //initialization is a to be added feature for now just wait a cycles then go to idle
    val state = RegInit(ControllerState.initialization)
    //hold read data
    val read_data_reg = Reg(UInt(p.data_width.W))
    //counter for read data being valid
    val cas_counter = Counter(p.wanted_cas_latency)
    //counter to terminate write
    val terminate_write = Counter(p.t_rw_cycles)
    //cycles to spam NOPs for SDRAM initialization
    val cycles_for_100us = (Duration(100, MICROSECONDS).toNanos.toInt /p.period.toFloat).ceil.toInt
    //the extra 3 cycles are for the 1 precharge and 2 auto refreshes need for programming SDRAM
    val hundred_micro_sec_counter = Counter(cycles_for_100us + 3)
    //active to read or write counter
    val active_to_rw_counter = Counter(p.active_to_rw_delay)

    //Default SDRAM signals
    io.sdram_control.address_bus := DontCare
    //io.sdram_control.data_out_and_in := DontCare
    io.sdram_control.cs := DontCare
    io.sdram_control.ras := DontCare
    io.sdram_control.cas := DontCare
    io.sdram_control.we := DontCare
    //other outputs
    io.state_out := state
    io.read_data_valid(0) := false.B
    io.write_data_valid(0) := false.B 
    switch(state){
        is(ControllerState.initialization){
            //for now just wait the cas latency
            //later each sdram will require its own init
            //module
            //1. NOPs for 100us
            //2. A Precharge 
            //3. 2 Auto Refresh
            //4. In Mode Programming mode
            //nop command for 100us
            io.sdram_control.cs := false.B
            io.sdram_control.ras := true.B
            io.sdram_control.cas := true.B
            io.sdram_control.we := true.B 
            hundred_micro_sec_counter.inc()
            //time to precharge
            when(hundred_micro_sec_counter.value === cycles_for_100us.U){
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := true.B
                io.sdram_control.we := false.B  
            } .elsewhen((hundred_micro_sec_counter.value === (cycles_for_100us + 1).U) | (hundred_micro_sec_counter.value === (cycles_for_100us + 2).U )){
                //time to auto refresh
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := false.B
                io.sdram_control.we := true.B  
            } .elsewhen(hundred_micro_sec_counter.value === (cycles_for_100us + 3).U){
                //time to program 
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := false.B
                io.sdram_control.we := false.B    
                //address holds programmed options
                //12'b00_wb_opcode_cas_bT_bL
                io.sdram_control.address_bus := Cat(0.U(2.W),p.write_burst,p.opcode,p.cas_latency,p.burst_type,p.burst_length)
                hundred_micro_sec_counter.reset()
                state := ControllerState.idle
            }
        }
        is(ControllerState.idle){
            state := ControllerState.idle
            //address holds row right now
            val go_to_active = io.read_start.exists(identity) | io.write_start.exists(identity) 
            //nop command
            io.sdram_control.cs := false.B
            io.sdram_control.ras := true.B
            io.sdram_control.cas := true.B
            io.sdram_control.we := true.B 
            when(go_to_active){
                state := ControllerState.active
                //active command - make this a function 
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := true.B
                io.sdram_control.we := true.B
                when(io.read_start.exists(identity)){
                    io.sdram_control.address_bus := io.read_row_addresses(0)
                } .otherwise{
                    io.sdram_control.address_bus := io.write_row_addresses(0)
                }
            }
        }
        is(ControllerState.active){
            state := ControllerState.active
            //read priority for now
            val we_are_reading = io.read_start.exists(identity)
            val we_are_writing = io.write_start.exists(identity)
            active_to_rw_counter.inc()
            //nop command
            io.sdram_control.cs := false.B
            io.sdram_control.ras := true.B
            io.sdram_control.cas := true.B
            io.sdram_control.we := true.B 
            //address bus needs to hold row address
            io.sdram_control.address_bus := io.read_row_addresses(0)
            when(we_are_reading & active_to_rw_counter.value === (p.active_to_rw_delay.U - 1.U)){
                state := ControllerState.reading
                //read command 
                io.sdram_control.cs := false.B
                io.sdram_control.ras := true.B
                io.sdram_control.cas := false.B
                io.sdram_control.we := true.B 
                //address bus now holds col address
                io.sdram_control.address_bus := io.read_col_addresses(0)
                cas_counter.reset()
            } .elsewhen(we_are_writing & active_to_rw_counter.value === (p.active_to_rw_delay.U - 1.U)){
                state := ControllerState.writing
                //write command 
                io.sdram_control.cs := false.B
                io.sdram_control.ras := true.B
                io.sdram_control.cas := false.B
                io.sdram_control.we := false.B
                //address bus now holds col address
                io.sdram_control.address_bus := io.write_col_addresses(0)
            }
        }
        is(ControllerState.reading){
            state := ControllerState.reading
            cas_counter.inc()
            //nop command
            io.sdram_control.cs := false.B
            io.sdram_control.ras := true.B
            io.sdram_control.cas := true.B
            io.sdram_control.we := true.B 
            when(cas_counter.value === p.cas_latency){
                //data is valid
                io.read_data_valid(0) := true.B
                //precharge command
                //io.read_data := read_data_reg
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := true.B
                io.sdram_control.we := false.B  
                state := ControllerState.idle
            }
        }
        is(ControllerState.writing){
            when(terminate_write.value === p.t_rw_cycles.U){
                //precharge command
                io.sdram_control.cs := false.B
                io.sdram_control.ras := false.B
                io.sdram_control.cas := true.B
                io.sdram_control.we := false.B  
                state := ControllerState.idle 
            }
        }
    }
}