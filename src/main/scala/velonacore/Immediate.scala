package velonamp.velonacore

import chisel3._
import chisel3.util._

import velonamp.common.ISA

class Immediate extends Module {
  val io = IO(new Bundle {
    val ctrl_imm   = Input(CTRL.IMM())
    val instr_data = Input(UInt(ISA.INSTR_WIDTH.W))
    val imm        = Output(UInt(ISA.REG_WIDTH.W))
  })

  val imm = Wire(UInt(ISA.REG_WIDTH.W))

  when(io.ctrl_imm === CTRL.IMM.loadi) {
    imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt()
  }
    .elsewhen(io.ctrl_imm === CTRL.IMM.shl1) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.shl2) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 2
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.loadhi) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 8
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.loadh2i) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 16
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.loadh3i) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 24
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.branch) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1
    }
    .elsewhen(io.ctrl_imm === CTRL.IMM.jal) { imm := Log2(ISA.INSTR_WIDTH.U) }
    .otherwise { imm := 0.U }

  io.imm := imm
}
