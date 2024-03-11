import chisel3._
import chisel3.util._

object MemCommand extends ChiselEnum {
  val nop, active, read, write, terminate, precharge, refresh, mode = Value
}

object MemModes extends ChiselEnum {
  val burstLenMask = 3.U
  val burstPageBit = 2
  val burstTypeBit = 3
  def casLatency(i: chisel3.UInt) = i(5, 4)
  val burstWriteBit = 6
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

class MemModelCASEntry(addrWidth: Int, bankWidth: Int) extends Bundle {
  val isWrite = Bool()
  val precharge = Bool()
  val addr = UInt(addrWidth.W)
  val bankSel = UInt(bankWidth.W)
}

class MemModel(width: Int, banks: Int, rowWidth: Int = 9, colWidth: Int = 6) extends Module {
  val bankWidth = log2Ceil(banks)
  require(colWidth < rowWidth)
  require(banks > 1)
  require(colWidth > 3)
  require(rowWidth > MemModes.burstWriteBit)
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
      val opAddr = Valid(new MemModelCASEntry(colWidth, bankWidth))
    }
  })
  io.rData := DontCare

  val dram = SyncReadMem(1 << (bankWidth + rowWidth + colWidth + width), Bool())
  val bankRow = RegInit(VecInit.fill(banks)(0.U(rowWidth.W)))
  val bankRowValid = RegInit(VecInit.fill(banks)(false.B))
  // Ignore the two reserved bits so we don't have to cat bankWidth
  val mode = RegInit(16.U((MemModes.burstWriteBit + 1).W))

  // Read CAS registers/connections
  val opData = Reg(new MemModelCASEntry(colWidth, bankWidth))
  val casRegister = Module(new AdjustableShiftRegister(2, new MemModelCASEntry(colWidth, bankWidth)))
  casRegister.io.shift := MemModes.casLatency(mode) - 1.U
  casRegister.io.input.bits.isWrite := io.cmd === MemCommand.write
  casRegister.io.input.bits.precharge := io.addr(autoPrechargeBit)
  casRegister.io.input.bits.addr := io.addr(colWidth, 0)
  casRegister.io.input.bits.bankSel := io.bankSel
  casRegister.io.input.valid := io.cmd.isOneOf(MemCommand.read, MemCommand.write)
  val opRunning = RegInit(false.B)
  val opStartAt = RegEnable(casRegister.io.output.bits.addr, casRegister.io.output.valid)
  io.debug.opAddr.valid := opRunning
  io.debug.opAddr.bits := opData

  // Read/write logic
  // These should technically be interrupted when the current command is not NOP
  // but I'll let it slide for now
  when (casRegister.io.output.valid || opRunning) {
    val src = Mux(casRegister.io.output.valid, casRegister.io.output.bits, opData)
    opData := src
    val realAddr = Cat(src.bankSel, bankRow(io.bankSel), src.addr)
    when (bankRowValid(src.bankSel)) {
      when (src.isWrite && io.writeEnable) {
        for (i <- 0 until width) {
          when (io.rwMask(i)) {
            dram(Cat(realAddr, i.U(width.W))) := io.wData(i)
          }
        }
      } .otherwise {
        // Apparently this line pushes Scala's poor memory manager over the edge!
        // Reduced the sizes of things for now
        io.rData := VecInit.tabulate(width){i => Mux(io.rwMask(i), dram(Cat(realAddr, i.U(width.W))), false.B)}.asUInt
      }
    }

    // Address increment logic
    val willRun = Wire(Bool())
    val nxt = src.addr + 1.U
    when (mode(MemModes.burstPageBit)) {
      opData.addr := nxt
      willRun := casRegister.io.output.valid || nxt =/= opStartAt
    } .elsewhen ((mode & MemModes.burstLenMask) === 0.U || (src.isWrite && mode(MemModes.burstWriteBit))) {
      willRun := false.B
    } .otherwise {
      val thing = Cat(src.addr(colWidth - 1, 3), Mux((mode & MemModes.burstLenMask) === 3.U, nxt(2), src.addr(2)),
        Mux((mode & MemModes.burstLenMask) >= 2.U, nxt(1), src.addr(1)), nxt(0))
      opData.addr := thing
      willRun := casRegister.io.output.valid || thing =/= opStartAt
    }

    // Precharge if needed
    opRunning := willRun
    when (!willRun && !src.isWrite && src.precharge) {
      bankRowValid(io.bankSel) := false.B
    }
  }

  // TODO: Parameterize number of cycles needed to refresh
  // Also make a separate counter that resets when refresh is low
  // so we have to hold refresh for a certain number of cycles for it to be effective
  // maybe? the first thing is probably sufficient honestly
  val refreshCounter = RegInit(0.U(log2Ceil(2048).W))
  io.debug.refresh := refreshCounter
  when (io.cmd === MemCommand.refresh && io.commandEnable) {
    when (refreshCounter > 0.U) {
      refreshCounter := refreshCounter - 1.U
    }
  } .elsewhen (refreshCounter >= 2048.U) {
    // While it would be nice to actually do something here
    // generating any kind of hardware that operates over any decently large section of memory
    // would kill sbt. So we'll likely add something to start killing commands at this point
  } .otherwise {
    refreshCounter := refreshCounter + 1.U
  }

  when (!io.commandEnable) {
    // do nothing
  } .elsewhen (io.cmd === MemCommand.active) {
    // Only activate a bank row if it is valid
    when (!bankRowValid(io.bankSel)) {
      bankRow(io.bankSel) := io.addr
      bankRowValid(io.bankSel) := true.B
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
  } .elsewhen (io.cmd === MemCommand.terminate) {
    opRunning := false.B
  }
}
