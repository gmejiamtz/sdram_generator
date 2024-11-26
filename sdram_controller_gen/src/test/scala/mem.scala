// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._
import scala.concurrent.duration._

class ShiftRegTest extends AnyFreeSpec with ChiselScalatestTester {
  "Test shift; fixed CAS" in {
    val slots = 2
    test(new AdjustableShiftRegister(slots, new MemModelCASEntry(1, 1))) {
      dut =>
        dut.io.shift.poke(slots.U)
        for (i <- 0 to slots) {
          dut.clock.step()
          dut.io.output.valid.expect(false.B)
        }
        dut.io.input.bits.precharge.poke(true.B)
        dut.io.input.valid.poke(true.B)
        dut.clock.step()
        dut.io.input.valid.poke(false.B)
        dut.io.output.valid.expect(false.B)
        dut.clock.step()
        dut.io.output.valid.expect(true.B)
    }
  }
}

class MemoryModelTest extends AnyFreeSpec with ChiselScalatestTester {
  "Test read and write; single cell, full mask" in {
    val width = 8
    val banks = 2
    test(new MemModel(width, 2048, banks))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.io.addr.poke(16.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.clock.step()
        dut.io.bankSel.poke(0.U)
        dut.io.rwMask.poke(((1 << width) - 1).U)
        dut.io.addr.poke(1.U)
        dut.io.cmd.poke(MemCommand.active)
        dut.io.commandEnable.poke(true.B)
        dut.clock.step()
        // Write 0xAA to bank 0, row 1, col 2
        dut.io.addr.poke(2.U)
        dut.io.wData.poke((0xAA).U)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        dut.io.writeEnable.poke(false.B)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.rData.expect(0xAA.U)
        // Check col 1 to see if it is still 0
        dut.io.addr.poke(1.U)
        dut.clock.step()
        dut.io.rData.expect(0.U)
        // Check row 2, col 2
        dut.io.addr.poke(2.U)
        dut.io.cmd.poke(MemCommand.precharge)
        dut.clock.step()
        dut.io.cmd.poke(MemCommand.active)
        dut.clock.step()
        dut.io.cmd.poke(MemCommand.read)
        dut.io.rData.expect(0.U)
      }
  }

  "Test read and write; single cell, lower nibble mask" in {
    val width = 8
    val banks = 2
    test(new MemModel(width, 2048, banks))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.io.addr.poke(16.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.clock.step()
        dut.io.bankSel.poke(0.U)
        dut.io.rwMask.poke(0xFF.U)
        dut.io.addr.poke(1.U)
        dut.io.cmd.poke(MemCommand.active)
        dut.io.commandEnable.poke(true.B)
        dut.clock.step()
        // Write 0xAA
        dut.io.addr.poke(2.U)
        dut.io.wData.poke((0xAA).U)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        // Read masked 0xAA
        dut.io.writeEnable.poke(false.B)
        dut.io.rwMask.poke(0x0F.U)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.rData.expect(0x0A.U)
        // Write masked 0x55
        dut.io.wData.poke(0x55)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        // Read 0x55, blended with 0xAA
        dut.io.writeEnable.poke(false.B)
        dut.io.rwMask.poke(0xFF.U)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.rData.expect(0xA5.U)
      }
  }

  "Test burst read, individual write" in {
    val width = 8
    val banks = 2
    test(new MemModel(width, 2048, banks))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.io.bankSel.poke(0.U)
        dut.io.rwMask.poke(((1 << width) - 1).U)
        dut.io.addr.poke(81.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.io.commandEnable.poke(true.B)
        dut.clock.step()
        dut.io.cmd.poke(MemCommand.active)
        dut.clock.step()
        // Write 0xAA,0x55 to bank 0, row 1, col 2-3
        dut.io.addr.poke(2.U)
        dut.io.wData.poke((0xAA).U)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        dut.io.addr.poke(3.U)
        dut.io.wData.poke(0x55.U)
        dut.clock.step()
        // Read back in reverse order
        dut.io.writeEnable.poke(false.B)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.rData.expect(0x55.U)
        dut.io.cmd.poke(MemCommand.nop)
        dut.clock.step()
        dut.io.rData.expect(0xAA.U)
      }
  }

  "Test burst read, burst write" in {
    val width = 8
    val banks = 2
    test(new MemModel(width, 2048, banks))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.io.bankSel.poke(0.U)
        dut.io.rwMask.poke(((1 << width) - 1).U)
        dut.io.addr.poke(17.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.io.commandEnable.poke(true.B)
        dut.clock.step()
        dut.io.addr.poke(1.U)
        dut.io.cmd.poke(MemCommand.active)
        dut.clock.step()
        // Write 0xAA,0x55 to bank 0, row 1, col 2-3
        dut.io.addr.poke(2.U)
        dut.io.wData.poke((0xAA).U)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        dut.io.wData.poke(0x55.U)
        dut.io.cmd.poke(MemCommand.nop)
        dut.clock.step()
        // Read back in reverse order
        dut.io.writeEnable.poke(false.B)
        dut.io.addr.poke(3.U)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.rData.expect(0x55.U)
        dut.io.cmd.poke(MemCommand.nop)
        dut.clock.step()
        dut.io.rData.expect(0xAA.U)
      }
  }

  "Test CAS latency" in {
    val width = 8
    val banks = 2
    test(new MemModel(width, 2048, banks))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.io.bankSel.poke(0.U)
        dut.io.rwMask.poke(((1 << width) - 1).U)
        dut.io.addr.poke(17.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.io.commandEnable.poke(true.B)
        dut.clock.step()
        dut.io.addr.poke(1.U)
        dut.io.cmd.poke(MemCommand.active)
        dut.clock.step()
        // Write 0xAA,0x55 to bank 0, row 1, col 2-3
        dut.io.addr.poke(2.U)
        dut.io.wData.poke((0xAA).U)
        dut.io.cmd.poke(MemCommand.write)
        dut.io.writeEnable.poke(true.B)
        dut.clock.step()
        dut.io.wData.poke(0x55.U)
        dut.io.cmd.poke(MemCommand.nop)
        dut.clock.step()
        dut.io.addr.poke(33.U)
        dut.io.cmd.poke(MemCommand.mode)
        dut.io.writeEnable.poke(false.B)
        dut.clock.step()
        // Read back in reverse order
        dut.io.addr.poke(3.U)
        dut.io.cmd.poke(MemCommand.read)
        dut.clock.step()
        dut.io.cmd.poke(MemCommand.nop)
        dut.clock.step()
        dut.io.rData.expect(0x55.U)
        dut.clock.step()
        dut.io.rData.expect(0xAA.U)
      }
  }
}
