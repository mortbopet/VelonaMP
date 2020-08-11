package VelonaCore

import chisel3._
import chisel3.experimental.ChiselEnum

object CTRL {
  object ALU extends ChiselEnum {
    val nop, add, sub, shra, and, or, xor, loadi, loadhi, loadh2i, loadh3i =
      Value;
  }

  object MEM extends ChiselEnum { val nop, wr, rd = Value; }

  object ACC_SRC extends ChiselEnum { val acc, alu, reg, mem = Value; }

  object ALU_OP1 extends ChiselEnum { val acc, pc, addr = Value; }

  object ALU_OP2 extends ChiselEnum { val reg, imm = Value; }

  object MEM_SIZE extends ChiselEnum { val byte, half, word = Value; }

  object BR extends ChiselEnum {
    val nop, br, brz, brnz, brp, brn = Value;
  }

  object IMM extends ChiselEnum {
    val nop, shl1, shl2, branch, loadi, loadhi, loadh2i, loadh3i, jal = Value;
  }
}

class Control extends Module {

  val io = IO(new Bundle {
    val op = Input(ISA.Op())

    val ctrl_alu = Output(CTRL.ALU())
    val ctrl_acc = Output(CTRL.ACC_SRC())
    val ctrl_imm = Output(CTRL.IMM())
    val ctrl_alu_op1 = Output(CTRL.ALU_OP1())
    val ctrl_alu_op2 = Output(CTRL.ALU_OP2())
    val ctrl_br = Output(CTRL.BR())
    val ctrl_dm_op = Output(CTRL.MEM())
    val ctrl_mem_size = Output(CTRL.MEM_SIZE())
    val ctrl_reg_op = Output(CTRL.MEM())
  })

  // ALU control
  when(
    List(
      ISA.Op.addr,
      ISA.Op.addi,
      ISA.Op.br,
      ISA.Op.brz,
      ISA.Op.brnz,
      ISA.Op.brp,
      ISA.Op.brn,
      ISA.Op.jal,
      ISA.Op.ldind,
      ISA.Op.ldindb,
      ISA.Op.ldindh,
      ISA.Op.stind,
      ISA.Op.stindb,
      ISA.Op.stindh
    ).contains(io.op).B
  ) {
    io.ctrl_alu := CTRL.ALU.add
  }
    .elsewhen(List(ISA.Op.subr, ISA.Op.subi).contains(io.op).B) {
      io.ctrl_alu := CTRL.ALU.sub
    }
    .elsewhen(List(ISA.Op.andi, ISA.Op.andr).contains(io.op).B) {
      io.ctrl_alu := CTRL.ALU.and
    }
    .elsewhen(List(ISA.Op.ori, ISA.Op.orr).contains(io.op).B) {
      io.ctrl_alu := CTRL.ALU.or
    }
    .elsewhen(List(ISA.Op.xori, ISA.Op.xorr).contains(io.op).B) {
      io.ctrl_alu := CTRL.ALU.xor
    }
    .elsewhen(io.op === ISA.Op.shra) { io.ctrl_alu := CTRL.ALU.shra }
    .elsewhen(io.op === ISA.Op.loadi) { io.ctrl_alu := CTRL.ALU.loadi }
    .elsewhen(io.op === ISA.Op.loadhi) { io.ctrl_alu := CTRL.ALU.loadhi }
    .elsewhen(io.op === ISA.Op.loadh2i) { io.ctrl_alu := CTRL.ALU.loadh2i }
    .elsewhen(io.op === ISA.Op.loadh3i) { io.ctrl_alu := CTRL.ALU.loadh3i }
    .otherwise { io.ctrl_alu := CTRL.ALU.nop }

  // Accumulator source control
  when(
    List(
      ISA.Op.addr,
      ISA.Op.addi,
      ISA.Op.subr,
      ISA.Op.subi,
      ISA.Op.shra,
      ISA.Op.loadi,
      ISA.Op.andr,
      ISA.Op.andi,
      ISA.Op.orr,
      ISA.Op.ori,
      ISA.Op.xorr,
      ISA.Op.xori,
      ISA.Op.loadhi,
      ISA.Op.loadh2i,
      ISA.Op.loadh3i
    ).contains(io.op).B
  ) {
    io.ctrl_acc := CTRL.ACC_SRC.alu
  }
    .elsewhen(
      List(ISA.Op.ldind, ISA.Op.ldindb, ISA.Op.ldindh).contains(io.op).B
    ) { io.ctrl_acc := CTRL.ACC_SRC.mem }
    .elsewhen(io.op === ISA.Op.load) { io.ctrl_acc := CTRL.ACC_SRC.reg }
    .otherwise { io.ctrl_acc := CTRL.ACC_SRC.acc }

  // Data memory control
  when(List(ISA.Op.ldind, ISA.Op.ldindb, ISA.Op.ldindh).contains(io.op).B) {
    io.ctrl_dm_op := CTRL.MEM.rd
  }
    .elsewhen(
      List(ISA.Op.stind, ISA.Op.stindb, ISA.Op.stindh).contains(io.op).B
    ) {
      io.ctrl_dm_op := CTRL.MEM.wr
    }
    .otherwise { io.ctrl_dm_op := CTRL.MEM.nop }

  // Data memory access size control
  when(List(ISA.Op.ldindb, ISA.Op.stindb).contains(io.op).B) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.byte
  }.elsewhen(List(ISA.Op.ldindh, ISA.Op.stindh).contains(io.op).B) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.half
  }.elsewhen(List(ISA.Op.ldind, ISA.Op.stind).contains(io.op).B) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.word
  }

  // Register control
  when(
    List(
      ISA.Op.addr,
      ISA.Op.subr,
      ISA.Op.andr,
      ISA.Op.orr,
      ISA.Op.xorr,
      ISA.Op.load,
      ISA.Op.ldaddr
    ).contains(io.op).B
  ) {
    io.ctrl_reg_op := CTRL.MEM.rd
  }.elsewhen(List(ISA.Op.store, ISA.Op.jal).contains(io.op).B) {
    io.ctrl_reg_op := CTRL.MEM.wr
  }.otherwise { io.ctrl_reg_op := CTRL.MEM.nop }

  // Immediate control
  when(
    List(
      ISA.Op.addi,
      ISA.Op.subi,
      ISA.Op.andi,
      ISA.Op.ori,
      ISA.Op.xori,
      ISA.Op.loadi
    ).contains(io.op).B
  ) {
    io.ctrl_imm := CTRL.IMM.loadi
  }
    .elsewhen(io.op === ISA.Op.loadhi) { io.ctrl_imm := CTRL.IMM.loadhi }
    .elsewhen(io.op === ISA.Op.loadh2i) { io.ctrl_imm := CTRL.IMM.loadh2i }
    .elsewhen(io.op === ISA.Op.loadh3i) { io.ctrl_imm := CTRL.IMM.loadh3i }
    .elsewhen(io.op === ISA.Op.jal) { io.ctrl_imm := CTRL.IMM.jal }
    .elsewhen(
      List(ISA.Op.br, ISA.Op.brz, ISA.Op.brp, ISA.Op.brn, ISA.Op.brnz)
        .contains(io.op)
        .B
    ) {
      io.ctrl_imm := CTRL.IMM.branch
    }
    .elsewhen(List(ISA.Op.stindb, ISA.Op.ldindb).contains(io.op).B) {
      io.ctrl_imm := CTRL.IMM.loadi
    }
    .elsewhen(List(ISA.Op.stindh, ISA.Op.ldindh).contains(io.op).B) {
      io.ctrl_imm := CTRL.IMM.shl1
    }
    .elsewhen(List(ISA.Op.stind, ISA.Op.ldind).contains(io.op).B) {
      io.ctrl_imm := CTRL.IMM.shl2
    }
    .otherwise { io.ctrl_imm := CTRL.IMM.nop }

  // ALU Operand selection
  when(
    List(ISA.Op.jal, ISA.Op.br, ISA.Op.brz, ISA.Op.brnz, ISA.Op.brp, ISA.Op.brn)
      .contains(io.op)
      .B
  ) {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.pc
  }.elsewhen(
    List(
      ISA.Op.ldind,
      ISA.Op.ldindh,
      ISA.Op.ldindb,
      ISA.Op.stind,
      ISA.Op.stindh,
      ISA.Op.stindb
    ).contains(io.op).B
  ) {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.addr
  }.otherwise {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.acc
  }

  when(
    List(
      ISA.Op.addi,
      ISA.Op.subi,
      ISA.Op.andi,
      ISA.Op.xori,
      ISA.Op.jal,
      ISA.Op.br,
      ISA.Op.brz,
      ISA.Op.brnz,
      ISA.Op.brp,
      ISA.Op.brn,
      ISA.Op.ldind,
      ISA.Op.ldindh,
      ISA.Op.ldindb,
      ISA.Op.stind,
      ISA.Op.stindh,
      ISA.Op.stindb,
      ISA.Op.loadi,
      ISA.Op.loadhi,
      ISA.Op.loadh2i,
      ISA.Op.loadh3i
    ).contains(io.op).B
  ) {

    io.ctrl_alu_op2 := CTRL.ALU_OP2.imm
  }
    .elsewhen(
      List(ISA.Op.addr, ISA.Op.subr, ISA.Op.andr, ISA.Op.orr, ISA.Op.xorr)
        .contains(io.op)
        .B
    ) {
      io.ctrl_alu_op2 := CTRL.ALU_OP2.reg
    }
    .otherwise {
      io.ctrl_alu_op2 := CTRL.ALU_OP2.imm
    }

  // Branch unit control
  when(io.op === ISA.Op.br) { io.ctrl_br := CTRL.BR.br }
    .elsewhen(io.op === ISA.Op.brz) { io.ctrl_br := CTRL.BR.brz }
    .elsewhen(io.op === ISA.Op.brnz) { io.ctrl_br := CTRL.BR.brnz }
    .elsewhen(io.op === ISA.Op.br) { io.ctrl_br := CTRL.BR.brp }
    .elsewhen(io.op === ISA.Op.br) { io.ctrl_br := CTRL.BR.brn }
    .otherwise { io.ctrl_br := CTRL.BR.nop }
}
