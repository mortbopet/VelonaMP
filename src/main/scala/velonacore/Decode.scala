package velonamp.velonacore

import velonamp.common.ISA

import chisel3._
import chisel3.util._

class Decode extends Module {
  val io = IO(new Bundle {
    val op = Output(UInt())
    val instr_data = Input(UInt(ISA.INSTR_WIDTH.W))
  })

  // Priority decode based on the matched instructions. If no match is found,
  // defaults to a NOP operation
  val matchvector =
    ISA.Opcodes.map(x => (x._1 === io.instr_data(15, 8), x._2)) :+ (true.B, ISA.OP_nop)
  io.op := Mux1H(matchvector)
}

