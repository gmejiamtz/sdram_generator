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
        val block_name = "init_to_idle:\n"
        val assumption1 = s"\tassume property (@(posedge clock) disable iff (reset) (hundred_micro_sec_counter_value == $cycles_for_init_to_idle));\n"
        val main_property = s"\tassert property (@(posedge clock) disable iff (reset) io_state_out == 1 |=> io_state_out == 2);\n"
        val assert_block = block_name.concat(assumption1).concat(main_property)
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
        val block_name = "idle_to_active:\n"
        val assumption1 = s"\tassume property (@(posedge clock) disable iff (reset) (~refresh_outstanding));\n"
        val main_property = s"\tassert property (@(posedge clock) disable iff (reset) (io_state_out == 2) & (io_read_start | io_write_start) |=> io_state_out == 3);\n"
        val assert_block = block_name.concat(assumption1).concat(main_property)
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
        val active_to_rw_cycles = sdram_params.active_to_rw_delay + 1
        val block_name = "active_to_rw:\n"
        val assumption1 = s"\tassume property (@(posedge clock) disable iff (reset) (stated_read | started_write));\n"
        val assumption2 = s"\tassume property (@(posedge clock) disable iff (reset) (cas_counter_value == 0));\n"
        val main_property = s"\tassert property (@(posedge clock) disable iff (reset) (io_state_out == 3) & ($$past(io_state_out) == 2) |-> ##$active_to_rw_cycles ((io_state_out == 6) | (io_state_out == 7)));\n"
        val assert_block = block_name.concat(assumption1).concat(assumption2).concat(main_property)
        val updatedLines = lines :+ assert_block
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
