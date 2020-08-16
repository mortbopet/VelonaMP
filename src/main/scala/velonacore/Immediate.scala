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

  val imm = WireDefault(0.U)
  io.imm := imm

  switch(io.ctrl_imm) {
    is(CTRL.IMM.loadi) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt()
    }
    is(CTRL.IMM.shl1) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1
    }
    is(CTRL.IMM.shl2) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 2
    }
    is(CTRL.IMM.loadhi) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 8
    }
    is(CTRL.IMM.loadh2i) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 16
    }
    is(CTRL.IMM.loadh3i) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 24
    }
    is(CTRL.IMM.branch) {
      imm := io.instr_data(7, 0).asSInt().pad(ISA.REG_WIDTH).asUInt() << 1
    }
    is(CTRL.IMM.jal) { imm := Log2(ISA.INSTR_WIDTH.U) }
  }
}
