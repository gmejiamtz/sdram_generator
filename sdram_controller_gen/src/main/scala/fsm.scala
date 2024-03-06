import chisel3._
import chisel3.util._

object ControllerState extends ChiselEnum {
    val clearing, initialization, idle, active, refresh, nop1, reading, writing, precharge, nop2 = Value
}

case class SDRAMControllerParams(data_w: Int,addr_w: Int, num_read_channels: Int, num_write_channels: Int){
    require(num_read_channels > 0)
    require(num_write_channels > 0)
    val read_channels = num_read_channels
    val write_channels = num_write_channels
    val data_width = data_w
    val address_width = addr_w
    val cas_latency = 3
    //needs to change to variable
    val active_to_rw_delay = 2
}

class ToSDRAM(p: SDRAMControllerParams) extends Bundle{
    val cs  = Output(Bool())
    val ras = Output(Bool())
    val cas = Output(Bool())
    val we  = Output(Bool()) 
    val address_bus = Output(UInt(p.address_width.W))
    val data_out_and_in = Output(UInt(p.address_width.W)) 
}

class SDRAMControllerIO(p: SDRAMControllerParams) extends Bundle {
    //require at least one read and one write channel
    require(p.num_read_channels > 0)
    require(p.num_write_channels > 0)
    //read channels - for now vec is a vec of 1
    val read_addresses = Input(Vec(p.num_read_channels, UInt(p.data_width.W)))
    val read_data = Output(Vec(p.num_read_channels, Valid(UInt(p.data_width.W))))
    //read start 
    val read_start = Input(Vec(p.num_read_channels, Bool()))
    //write channels
    val write_addresses = Input(Vec(p.num_read_channels, UInt(p.data_width.W)))
    val write_data = Output(Vec(p.num_read_channels, Flipped(Valid(UInt(p.data_width.W)))))
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
    //address bus is for both row and col addr just used differently after active state
    val row_addr_reg = RegInit(VecInit(Seq.fill(p.num_read_channels)(0.U(p.address_width.W))))
    val col_addr_reg = RegInit(VecInit(Seq.fill(p.num_read_channels)(0.U(p.address_width.W))))
    //hard coded
    val cas_counter = Counter(p.cas_latency)
    switch(state){
        is(ControllerState.initialization){
            //for now just wait the cas latency
            //later each sdram will require its own init
            //module
            //1. NOPs for 100us
            //2. A Precharge 
            //3. 2 Auto Refresh
            //4. In Mode Programming mode
            cas_counter.inc()
            when(cas_counter.value === (p.cas_latency - 1).U){
                state := ControllerState.idle
                cas_counter.reset()
            }
        }
        is(ControllerState.idle){
            state := ControllerState.idle
            //address holds row right now
            row_addr_reg := io.read_addresses(0) //just use 0 index for now
            val go_to_active = io.read_start.exists(identity) | io.write_start.exists(identity) 
            when(go_to_active){
                state := ControllerState.active
            }
        }
        is(ControllerState.active){
            state := ControllerState.active
            //read priority for now
            val we_are_reading = io.read_start.exists(identity)
            when(we_are_reading){
                state := ControllerState.reading
            } .otherwise{
                state := ControllerState.writing
            }
        }
        is(ControllerState.reading){
            ???
        }
    }
}