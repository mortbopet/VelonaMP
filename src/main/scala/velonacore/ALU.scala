package velonamp.velonacore

import velonamp.common.ISA
import velonamp.velonacore.CTRL.ALU

import chisel3._
import chisel3.util._

class ALU extends Module {
  val io = IO(new Bundle {
    val ctrl_alu = Input(CTRL.ALU())
    val op1  = Input(SInt(ISA.REG_WIDTH.W))
    val op2  = Input(SInt(ISA.REG_WIDTH.W))
    val out  = Output(SInt(ISA.REG_WIDTH.W))
  })

  when(io.ctrl_alu === CTRL.ALU.nop) { io.out := 0.S }
    .elsewhen(io.ctrl_alu === CTRL.ALU.add) { io.out := io.op1 + io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.sub) { io.out := io.op1 - io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.shra) { io.out := io.op1 >> 1 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.and) { io.out := io.op1 & io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.or) { io.out := io.op1 | io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.xor) { io.out := io.op1 ^ io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.loadi) { io.out := io.op2 }
    .elsewhen(io.ctrl_alu === CTRL.ALU.loadhi) {
      io.out := Cat(io.op2(31, 8), io.op1(7, 0)).asSInt()
    }
    .elsewhen(io.ctrl_alu === CTRL.ALU.loadh2i) {
      io.out := Cat(io.op2(31, 16), io.op1(15, 0)).asSInt()
    }
    .elsewhen(io.ctrl_alu === CTRL.ALU.loadh3i) {
      io.out := Cat(io.op2(31, 24), io.op1(23, 0)).asSInt()
    }
    .otherwise { io.out := DontCare }

}
