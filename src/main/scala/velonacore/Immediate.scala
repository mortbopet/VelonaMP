package velonamp.velonacore

import chisel3._
import chisel3.util._

class Immediate extends Module {
  val io = IO(new Bundle {
    val op = Input(CTRL.IMM())
    val instr = Input(UInt(ISA.INSTR_WIDTH.W))
    val imm = Output(UInt(ISA.REG_WIDTH.W))
  })

  val imm = Wire(UInt(ISA.REG_WIDTH.W))

  when(io.op === CTRL.IMM.loadi) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() }
  .elsewhen(io.op === CTRL.IMM.shl1) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1 }
  .elsewhen(io.op === CTRL.IMM.shl2) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 2 }
  .elsewhen(io.op === CTRL.IMM.loadhi) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 8 }
  .elsewhen(io.op === CTRL.IMM.loadh2i) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 16}
  .elsewhen(io.op === CTRL.IMM.loadh3i) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 24}
  .elsewhen(io.op === CTRL.IMM.branch) { imm := io.instr(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1 }
  .elsewhen(io.op === CTRL.IMM.jal) { imm := Log2(ISA.INSTR_WIDTH.U) }
  .otherwise { imm := 0.U }

  io.imm := imm
}
