import chisel3._
import chisel3.util._

object MemCommand extends ChiselEnum {
    val nop, bankSel, read, write, endBurst, precharge, refresh, mode
}

class MemModel(width: Int, addrWidth: Int, bankWidth: Int) {
  val io = IO(new Bundle
    val writeEnable = Input(Bool())
    val commandEnable = Input(Bool())
    val addr = Input(UInt(addrWidth.W))
    val bankSel = Input(UInt(bankWidth.W))
    val cmd = Input(MemCommand)
    val rData = Output(UInt(width.W))
    val wData = Input(UInt(width.W))
  ))

  val dram = SyncReadMem(1 << (bankWidth + addrWidth), UInt(width.W)))
  val bank = RegInit(0.U(bankWidth.W))
  io.rData := DontCare
  when (!commandEnable || cmd === MemCommand.nop) {
  } .elsewhen (io.cmd === MemCommand.bankSel) {
    bank := io.bankSel
  } .elsewhen (io.cmd === MemCommand.read) {
    io.rData := dram(Cat(bank, io.addr))
  } .elsewhen (cmd === MemCommand.write) {
    when (io.writeEnable) {
      dram(Cat(bank, io.addr)) := io.wData
    }
  } .otherwise {
    ???
  }
}
