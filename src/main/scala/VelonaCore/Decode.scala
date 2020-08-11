package VelonaCore

import chisel3._
import chisel3.util._

class Decode extends Module {
  val io = IO(new Bundle {
    val instr = Input(UInt(ISA.INSTR_WIDTH.W))
    val op = Output(ISA.Op())
  })

  // Priority decode based on the matched instructions.
  // If no match is found, defaults to ISA.Op.nop
  val matchvector =
    ISA.Opcodes.map(x => (x._1 === io.instr, x._2)) :+ (true.B, ISA.Op.nop)
  io.op := Mux1H(matchvector)
}
