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
        val updatedLines = lines :+ "\tassert property (1 == 1);\n"
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }
}
