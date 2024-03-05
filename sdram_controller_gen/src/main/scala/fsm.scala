import chisel3._
import chisel3.util._

object ControllerState extends ChiselEnum {
    val clearing, idle, active, refresh, nop1, reading, writing, precharge, nop2 = Value
}

//allows for a variable ammount of read and write channels
class ControllerReadChannel(width: Int) extends Bundle{
    val read_addr_i = UInt(width.W)
    val read_data_o = Decoupled(UInt(width.W))
}

class ControllerWriteChannel(width: Int) extends Bundle{
    val write_addr_i = UInt(width.W)
    val write_data_o = Flipped(Decoupled(UInt(width.W)))
}

case class SDRAMControllerParams(width: Int, num_read_channels: Int, num_write_channels: Int){
    require(num_read_channels > 0)
    require(num_write_channels > 0)
    val read_channels = num_read_channels
    val write_channels = num_write_channels
    val data_width = width
}

class SDRAMControllerIO(p: SDRAMControllerParams) extends Bundle {
    //require at least one read and one write channel
    require(p.num_read_channels > 0)
    require(p.num_write_channels > 0)
    //read channels
    val read_addresses = Input(Vec(p.num_read_channels, UInt(p.data_width.W)))
    val read_data = Output(Vec(p.num_read_channels, Decoupled(UInt(p.data_width.W))))
    //write channels
    val write_addresses = Input(Vec(p.num_read_channels, UInt(p.data_width.W)))
    val write_data = Output(Vec(p.num_read_channels, Flipped(Decoupled(UInt(p.data_width.W)))))
    //debug purposes
    val state_out = Output(ControllerState())
}

class SDRAMController(p: SDRAMControllerParams) extends Module{
    val io = IO(new SDRAMControllerIO(p))
}