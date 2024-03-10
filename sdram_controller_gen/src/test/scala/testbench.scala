// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html

import chisel3._
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec
import chisel3.experimental.BundleLiterals._

class UnitTest1 extends AnyFreeSpec with ChiselScalatestTester {
  "example test that succeeds" in {
    val params = new SDRAMControllerParams(16,12,1,1,0,0,3,0,0)
    test(new SDRAMController(params)) {dut =>
      val obtained = 42
      val expected = 42
      println("test")
      assert(obtained == expected)
    }
  }
}