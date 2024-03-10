
// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._
import scala.concurrent.duration._

class MemoryModelTest extends AnyFreeSpec with ChiselScalatestTester {
  "Test read and write; single cell, full mask" in {
    val width = 8
    val banks = 4
    test(new MemModel(width, banks)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
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
    val banks = 4
    test(new MemModel(width, banks)).withAnnotations(Seq(WriteVcdAnnotation)) {dut =>
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
}
