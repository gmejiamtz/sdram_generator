package sva
import sdram_general._
import java.nio.file.{Files, Paths, StandardOpenOption}
import scala.io.Source
//case class SDRAMVerilogFileParams(fileName: String) {
    //val num_lines = io.Source.fromFile(fileName).getLines().size
//}

class SVA_Modifier(){
    def begin_formal_block(filePath: String): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val updatedLines = lines.dropRight(2) :+ "`ifdef FORMAL\n"
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    } 
    def end_formal_block(filePath: String): Unit = {
        val lines = Source.fromFile(filePath).getLines().toList
        val updatedLines = lines :+ "`endif\nendmodule\n"
        Files.write(
            Paths.get(filePath),
            updatedLines.mkString("\n").getBytes,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    } 
}

