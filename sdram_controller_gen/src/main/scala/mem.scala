import chisel3._
import chisel3.util._

object MemCommand extends ChiselEnum {
  val nop, bankSel, read, write, endBurst, precharge, refresh, mode = Value
  val active = bankSel
}

object MemModes extends ChiselEnum {
  val burstLenMask = 3.U
  val burstPageFlag = 4.U
  val burstTypeFlag = 8.U
  def casLatency(i: chisel3.UInt) = i(7, 5)
  val burstWriteFlag = 512.U
}

class MemModel(width: Int, banks: Int) {
  val rowWidth = 11
  val bankWidth = log2Ceil(banks)
  val colWidth = 8
  
  val io = IO(new Bundle
    val writeEnable = Input(Bool())
    val commandEnable = Input(Bool())
    val addr = Input(UInt(rowWidth.W))
    val bankSel = Input(UInt(bankWidth.W))
    val cmd = Input(MemCommand)
    val rData = Output(UInt(width.W))
    val wData = Input(UInt(width.W))
    val rwMask = Input(UInt(width.W))
  ))

  val dram = SyncReadMem(1 << (bankWidth + rowWidth + colWidth), UInt(width.W)))
  val bankRow = RegInit(VecInit(banks, 0.U(rowWidth.W)))
  val bankRowValid = RegInit(VecInit(banks, false.B))
  // Ignore the two reserved bits so we don't have to cat bankWidth
  val mode = RegInit(0.U(rowWidth.W))

  io.rData := DontCare
  val realAddr = Cat(io.bankSel, Cat(bankRow(io.bankSel), io.addr(colWidth, 0))))
  when (!commandEnable || cmd === MemCommand.nop) {
    // do nothing
  } .elsewhen (io.cmd === MemCommand.bankSel) {
    // Only activate a bank row if it is valid
    when (!bankRowValid(io.bankSel)) {
      bankRow(io.bankSel) := io.addr
      bankRowValid(io.bankSel) := true.B
    }
    // TODO handle bursts, adjustable CAS
    // need reimplementation of shiftreg due to adjustable CAS
    // assume will not change when outstanding command in queue
    // command starts being processed after CAS - 1 cycles, interrupting current cmd
  } .elsewhen (io.cmd === MemCommand.read) {
    when (bankRowValid(io.bankSel)) {
      io.rData := dram(realAddr) & io.rwMask
    }
  } .elsewhen (cmd === MemCommand.write) {
    when (io.writeEnable && bankRowValid(io.bankSel)) {
      dram(realAddr) := io.wData & io.rwMask
    }
  } .elsewhen (cmd === MemCommand.mode) {
    mode := addr
  } .elsewhen (cmd === MemCommand.precharge) {
    when (io.addr(10)) {
      // Precharge all banks when io.addr(10) high
      bankRowValid := VecInit(banks, false.B)
    } .otherwise {
      bankRowValid(io.bankSel) := false.B
    }
  // TODO: How should we verify refresh? Need to parameterize decay rate in cycles
  // Maybe stick a counter since last refresh per row/bank and start throwing errors after?
  // Probably want debug output for errors
  } .otherwise {
    ???
  }
}
