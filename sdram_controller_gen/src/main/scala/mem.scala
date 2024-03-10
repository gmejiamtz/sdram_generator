import chisel3._
import chisel3.util._

object MemCommand extends ChiselEnum {
  val nop, bankSel, read, write, endBurst, precharge, refresh, mode = Value
  val active = bankSel
}

object MemModes extends ChiselEnum {
  val burstLenMask = 3.U
  val burstPageBit = 2
  val burstTypeBit = 3
  def casLatency(i: chisel3.UInt) = i(7, 5)
  val burstWriteBit = 9
}

class AdjustableShiftRegister[T <: chisel3.Data](slots: Int, t: T) extends Module {
  val io = IO(new Bundle{
    val shift = Input(UInt(log2Ceil(slots + 1).W))
    val input = Flipped(Valid(t))
    val output = Valid(t)
  })
  val regs = Reg(Vec(slots, t))
  val valid = RegInit(VecInit.fill(slots)(false.B))
  when (io.shift === 0.U) {
    io.output.bits := io.input.bits
    io.output.valid := io.input.valid
  } .otherwise {
    io.output.valid := valid(0)
    when (valid(0)) {
      io.output.bits := regs(0)
    } .otherwise {
      io.output.bits := DontCare
    }
    for (i <- 1 until slots) {
      val j = (i - 1).U
      regs(j) := regs(i.U)
      valid(j) := valid(i.U)
    }
    // Forgetting -1 can lead to overflow, causing early shift
    // Interesting implementation...
    // If shift is 1, will be output on next cycle and so on
    // no i'm not doing an inductive proof for this i didn't take 201 you can't make me
    regs(io.shift - 1.U) := io.input.bits
    valid(io.shift - 1.U) := io.input.valid
  }
}

class MemModel(width: Int, banks: Int, rowWidth: Int = 11, colWidth: Int = 8) extends Module {
  val bankWidth = log2Ceil(banks)
  require(colWidth < rowWidth)
  val autoPrechargeBit = rowWidth - 1
  
  val io = IO(new Bundle{
    val writeEnable = Input(Bool())
    val commandEnable = Input(Bool())
    val addr = Input(UInt(rowWidth.W))
    val bankSel = Input(UInt(bankWidth.W))
    val cmd = Input(MemCommand())
    val rData = Output(UInt(width.W))
    val wData = Input(UInt(width.W))
    val rwMask = Input(UInt(width.W))
    val debug = new Bundle {
      val refresh = Output(UInt())
      val opAddr = Valid(UInt(colWidth.W))
    }
  })

  val dram = SyncReadMem(1 << (bankWidth + rowWidth + colWidth), UInt(width.W))
  val bankRow = RegInit(VecInit.fill(banks)(0.U(rowWidth.W)))
  val bankRowValid = RegInit(VecInit.fill(banks)(false.B))
  // Ignore the two reserved bits so we don't have to cat bankWidth
  val mode = RegInit(0.U(rowWidth.W))

  val opTimer = Reg(UInt(colWidth.W))
  val opRunning = RegInit(false.B)
  io.debug.opAddr.valid := opRunning
  io.debug.opAddr.bits := opTimer

  def updateAddr(isWrite: Boolean): Bool = {
    val willRun = Wire(Bool())
    // TODO figure out how interleave's bit manipulation works
    // it's not a simple xor 1 or reverse order
    val orig = Mux(opRunning, opTimer, io.addr(colWidth, 0))
    val nxt = orig + 1.U
    when (mode(MemModes.burstPageBit)) {
      opTimer := nxt
      willRun := nxt =/= io.addr(colWidth, 0)
    } .elsewhen ((mode & MemModes.burstLenMask) === 0.U || (if (isWrite) mode(MemModes.burstWriteBit) else false.B)) {
      willRun := false.B
    } .otherwise {
      val thing = Cat(opTimer(colWidth - 1, 3), Mux((mode & MemModes.burstLenMask) === 3.U, nxt(2), orig(2)),
        Mux((mode & MemModes.burstLenMask) >= 2.U, nxt(1), orig(1)), nxt(0))
      opTimer := thing
      willRun := thing =/= io.addr(colWidth, 0)
    }
    opRunning := willRun
    willRun
  }

  // TODO: Parameterize number of cycles needed to refresh
  // Also make a separate counter that resets when refresh is low
  // so we have to hold refresh for a certain number of cycles for it to be effective
  // maybe? the first thing is probably sufficient honestly
  val refreshCounter = RegInit(0.U(log2Ceil(2048).W))
  io.debug.refresh := refreshCounter
  when (io.cmd === MemCommand.refresh && io.commandEnable) {
    refreshCounter := refreshCounter - 1.U
  } .elsewhen (refreshCounter >= 2048.U) {
    // While it would be nice to actually do something here
    // generating any kind of hardware that operates over any decently large section of memory
    // would kill sbt. So we'll likely add something to start killing commands at this point
  } .otherwise {
    refreshCounter := refreshCounter + 1.U
  }

  io.rData := DontCare
  val realAddr = Cat(io.bankSel, bankRow(io.bankSel), Mux(opRunning, opTimer, io.addr(colWidth, 0)))
  when (!io.commandEnable || io.cmd === MemCommand.nop || io.cmd === MemCommand.refresh) {
    // do nothing
  } .elsewhen (io.cmd === MemCommand.bankSel) {
    // Only activate a bank row if it is valid
    when (!bankRowValid(io.bankSel)) {
      bankRow(io.bankSel) := io.addr
      bankRowValid(io.bankSel) := true.B
    }
    // TODO ajustable CAS
    // assume will not change when outstanding command in queue
    // command starts being processed after CAS - 1 cycles, interrupting current cmd
  } .elsewhen (io.cmd === MemCommand.read) {
    when (bankRowValid(io.bankSel)) {
      io.rData := dram(realAddr) & io.rwMask
      val willRun = updateAddr(false)
      // Check auto precharge
      when (!willRun && io.addr(autoPrechargeBit)) {
        bankRowValid(io.bankSel) := false.B
      }
    }
  } .elsewhen (io.cmd === MemCommand.write) {
    when (bankRowValid(io.bankSel)) {
      when (io.writeEnable) {
        dram(realAddr) := (io.wData & io.rwMask) | (dram(realAddr) & ~io.rwMask)
      }
      updateAddr(true)
    }
  } .elsewhen (io.cmd === MemCommand.mode) {
    mode := io.addr
  } .elsewhen (io.cmd === MemCommand.precharge) {
    when (io.addr(autoPrechargeBit)) {
      // Precharge all banks when MSB of io.addr high
      bankRowValid := VecInit.fill(banks)(false.B)
    } .otherwise {
      bankRowValid(io.bankSel) := false.B
    }
  } .otherwise {
    opRunning := false.B
  }
}
