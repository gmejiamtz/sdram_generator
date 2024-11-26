package sva
import sdram_general._
import java.nio.file.{Files, Paths, StandardOpenOption}
import scala.io.Source

class SVA_Modifier(path: String, sdram_params: SDRAMControllerParams){
    val filePath = path
    def begin_formal_block(): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val updatedLines = lines.dropRight(1) :+ "`ifdef FORMAL\n"
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    } 
    def end_formal_block(): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val updatedLines = lines :+ "`endif // FORMAL\nendmodule\n"
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    } 
    def init_to_idle_assertion(): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val cycles_for_init_to_idle = sdram_params.cycles_for_100us + 3
        val assert_block = s"init_to_idle_assert:\n\tassert property (@(posedge clock) disable iff (reset) io_state_out == 1 |-> ##$cycles_for_init_to_idle io_state_out == 2);\n"
        val updatedLines = lines :+ assert_block
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
    def idle_to_active_assertion(): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val cycles_for_init_to_idle = sdram_params.cycles_for_100us + 3
        val assert_block = s"idle_to_active_assert:\n\tassert property (@(posedge clock) disable iff (reset) (io_state_out == 2) & (io_read_start | io_write_start) |-> io_state_out == 3);\n"
        val updatedLines = lines :+ assert_block
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
    def active_to_rw_assertion(): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val active_to_rw_cycles = sdram_params.active_to_rw_delay
        val assert_block = s"active_to_rw_assert:\n\tassert property (@(posedge clock) disable iff (reset) (io_state_out == 3) & ($$past(io_state_out) == 2) |-> ##$active_to_rw_cycles ((io_state_out == 6) | (io_state_out == 7)));\n"
        val updatedLines = lines :+ assert_block
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
