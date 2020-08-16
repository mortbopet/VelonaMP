package velonamp.velonacore

import velonamp.common.ISA
import velonamp.velonacore.CTRL.ALU

import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    val ctrl_alu = Input(CTRL.ALU())
    val op1      = Input(SInt(ISA.REG_WIDTH.W))
    val op2      = Input(SInt(ISA.REG_WIDTH.W))
    val out      = Output(SInt(ISA.REG_WIDTH.W))
  })

  val out = WireDefault(0.S)
  io.out := out
  switch(io.ctrl_alu) {
    is(CTRL.ALU.nop) { out := 0.S }
    is(CTRL.ALU.add) { out := io.op1 + io.op2 }
    is(CTRL.ALU.sub) { out := io.op1 - io.op2 }
    is(CTRL.ALU.shra) { out := io.op1 >> 1 }
    is(CTRL.ALU.and) { out := io.op1 & io.op2 }
    is(CTRL.ALU.or) { out := io.op1 | io.op2 }
    is(CTRL.ALU.xor) { out := io.op1 ^ io.op2 }
    is(CTRL.ALU.loadi) { out := io.op2 }
    is(CTRL.ALU.loadhi) {
      out := Cat(io.op2(31, 8), io.op1(7, 0)).asSInt()
    }
    is(CTRL.ALU.loadh2i) {
      out := Cat(io.op2(31, 16), io.op1(15, 0)).asSInt()
    }
    is(CTRL.ALU.loadh3i) {
      out := Cat(io.op2(31, 24), io.op1(23, 0)).asSInt()
    }
  }
}
