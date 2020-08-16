package velonamp.velonacore

import chisel3.util._
import chisel3._
import chisel3.experimental.ChiselEnum

import chisel3.dontTouch

import velonamp.util._
import velonamp.common.ISA

object CTRL {
  object ALU extends ChiselEnum {
    val nop, add, sub, shra, and, or, xor, loadi, loadhi, loadh2i, loadh3i =
      Value;
  }
  object MEM      extends ChiselEnum { val nop, wr, rd = Value;        }
  object ACC_SRC  extends ChiselEnum { val acc, alu, reg, mem = Value; }
  object ALU_OP1  extends ChiselEnum { val acc, pc, addr = Value;      }
  object ALU_OP2  extends ChiselEnum { val reg, imm = Value;           }
  object MEM_SIZE extends ChiselEnum { val byte, half, word = Value;   }
  object BR extends ChiselEnum {
    val nop, br, brz, brnz, brp, brn = Value;
  }
  object IMM extends ChiselEnum {
    val nop, shl1, shl2, branch, loadi, loadhi, loadh2i, loadh3i, jal = Value;
  }
}

class Control extends Module {

  val io = IO(new Bundle {
    val op = Input(UInt())

    val ctrl_alu      = Output(CTRL.ALU())
    val ctrl_acc      = Output(CTRL.ACC_SRC())
    val ctrl_imm      = Output(CTRL.IMM())
    val ctrl_alu_op1  = Output(CTRL.ALU_OP1())
    val ctrl_alu_op2  = Output(CTRL.ALU_OP2())
    val ctrl_br       = Output(CTRL.BR())
    val ctrl_mem_op    = Output(CTRL.MEM())
    val ctrl_mem_size = Output(CTRL.MEM_SIZE())
    val ctrl_reg_op   = Output(CTRL.MEM())
  })

  // ALU control
  when(
    VecInit(
      ISA.OP_addr,
      ISA.OP_addi,
      ISA.OP_br,
      ISA.OP_brz,
      ISA.OP_brnz,
      ISA.OP_brp,
      ISA.OP_brn,
      ISA.OP_jal,
      ISA.OP_ldind,
      ISA.OP_ldindb,
      ISA.OP_ldindh,
      ISA.OP_stind,
      ISA.OP_stindb,
      ISA.OP_stindh
    ).contains(io.op)
  ) {
    io.ctrl_alu := CTRL.ALU.add
  }
    .elsewhen(VecInit(ISA.OP_subr, ISA.OP_subi).contains(io.op)) {
      io.ctrl_alu := CTRL.ALU.sub
    }
    .elsewhen(VecInit(ISA.OP_andi, ISA.OP_andr).contains(io.op)) {
      io.ctrl_alu := CTRL.ALU.and
    }
    .elsewhen(VecInit(ISA.OP_ori, ISA.OP_orr).contains(io.op)) {
      io.ctrl_alu := CTRL.ALU.or
    }
    .elsewhen(VecInit(ISA.OP_xori, ISA.OP_xorr).contains(io.op)) {
      io.ctrl_alu := CTRL.ALU.xor
    }
    .elsewhen(io.op === ISA.OP_shra) { io.ctrl_alu := CTRL.ALU.shra }
    .elsewhen(io.op === ISA.OP_loadi) { io.ctrl_alu := CTRL.ALU.loadi }
    .elsewhen(io.op === ISA.OP_loadhi) { io.ctrl_alu := CTRL.ALU.loadhi }
    .elsewhen(io.op === ISA.OP_loadh2i) { io.ctrl_alu := CTRL.ALU.loadh2i }
    .elsewhen(io.op === ISA.OP_loadh3i) { io.ctrl_alu := CTRL.ALU.loadh3i }
    .otherwise { io.ctrl_alu := CTRL.ALU.nop }

  // Accumulator source control
  when(
    VecInit(
      ISA.OP_addr,
      ISA.OP_addi,
      ISA.OP_subr,
      ISA.OP_subi,
      ISA.OP_shra,
      ISA.OP_loadi,
      ISA.OP_andr,
      ISA.OP_andi,
      ISA.OP_orr,
      ISA.OP_ori,
      ISA.OP_xorr,
      ISA.OP_xori,
      ISA.OP_loadhi,
      ISA.OP_loadh2i,
      ISA.OP_loadh3i
    ).contains(io.op)
  ) {
    io.ctrl_acc := CTRL.ACC_SRC.alu
  }
    .elsewhen(
      VecInit(ISA.OP_ldind, ISA.OP_ldindb, ISA.OP_ldindh).contains(io.op)
    ) { io.ctrl_acc := CTRL.ACC_SRC.mem }
    .elsewhen(io.op === ISA.OP_load) { io.ctrl_acc := CTRL.ACC_SRC.reg }
    .otherwise { io.ctrl_acc := CTRL.ACC_SRC.acc }

  // Data memory control
  when(VecInit(ISA.OP_ldind, ISA.OP_ldindb, ISA.OP_ldindh).contains(io.op)) {
    io.ctrl_mem_op := CTRL.MEM.rd
  }
    .elsewhen(
      VecInit(ISA.OP_stind, ISA.OP_stindb, ISA.OP_stindh).contains(io.op)
    ) {
      io.ctrl_mem_op := CTRL.MEM.wr
    }
    .otherwise { io.ctrl_mem_op := CTRL.MEM.nop }

  // Data memory access size control
  when(VecInit(ISA.OP_ldindb, ISA.OP_stindb).contains(io.op)) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.byte
  }.elsewhen(VecInit(ISA.OP_ldindh, ISA.OP_stindh).contains(io.op)) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.half
  }.elsewhen(VecInit(ISA.OP_ldind, ISA.OP_stind).contains(io.op)) {
    io.ctrl_mem_size := CTRL.MEM_SIZE.word
  }.otherwise { io.ctrl_mem_size := CTRL.MEM_SIZE.word }

  // Register control
  when(
    VecInit(
      ISA.OP_addr,
      ISA.OP_subr,
      ISA.OP_andr,
      ISA.OP_orr,
      ISA.OP_xorr,
      ISA.OP_load,
      ISA.OP_ldaddr
    ).contains(io.op)
  ) {
    io.ctrl_reg_op := CTRL.MEM.rd
  }.elsewhen(VecInit(ISA.OP_store, ISA.OP_jal).contains(io.op)) {
    io.ctrl_reg_op := CTRL.MEM.wr
  }.otherwise { io.ctrl_reg_op := CTRL.MEM.nop }

  // Immediate control
  when(
    VecInit(
      ISA.OP_addi,
      ISA.OP_subi,
      ISA.OP_andi,
      ISA.OP_ori,
      ISA.OP_xori,
      ISA.OP_loadi
    ).contains(io.op)
  ) {
    io.ctrl_imm := CTRL.IMM.loadi
  }
    .elsewhen(io.op === ISA.OP_loadhi) { io.ctrl_imm := CTRL.IMM.loadhi }
    .elsewhen(io.op === ISA.OP_loadh2i) { io.ctrl_imm := CTRL.IMM.loadh2i }
    .elsewhen(io.op === ISA.OP_loadh3i) { io.ctrl_imm := CTRL.IMM.loadh3i }
    .elsewhen(io.op === ISA.OP_jal) { io.ctrl_imm := CTRL.IMM.jal }
    .elsewhen(
      VecInit(ISA.OP_br, ISA.OP_brz, ISA.OP_brp, ISA.OP_brn, ISA.OP_brnz)
        .contains(io.op)

    ) {
      io.ctrl_imm := CTRL.IMM.branch
    }
    .elsewhen(VecInit(ISA.OP_stindb, ISA.OP_ldindb).contains(io.op)) {
      io.ctrl_imm := CTRL.IMM.loadi
    }
    .elsewhen(VecInit(ISA.OP_stindh, ISA.OP_ldindh).contains(io.op)) {
      io.ctrl_imm := CTRL.IMM.shl1
    }
    .elsewhen(VecInit(ISA.OP_stind, ISA.OP_ldind).contains(io.op)) {
      io.ctrl_imm := CTRL.IMM.shl2
    }
    .otherwise { io.ctrl_imm := CTRL.IMM.nop }

  // ALU Operand selection
  when(
    VecInit(ISA.OP_jal, ISA.OP_br, ISA.OP_brz, ISA.OP_brnz, ISA.OP_brp, ISA.OP_brn)
      .contains(io.op)

  ) {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.pc
  }.elsewhen(
    VecInit(
      ISA.OP_ldind,
      ISA.OP_ldindh,
      ISA.OP_ldindb,
      ISA.OP_stind,
      ISA.OP_stindh,
      ISA.OP_stindb
    ).contains(io.op)
  ) {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.addr
  }.otherwise {
    io.ctrl_alu_op1 := CTRL.ALU_OP1.acc
  }

  when(
    VecInit(
      ISA.OP_addi,
      ISA.OP_subi,
      ISA.OP_andi,
      ISA.OP_xori,
      ISA.OP_jal,
      ISA.OP_br,
      ISA.OP_brz,
      ISA.OP_brnz,
      ISA.OP_brp,
      ISA.OP_brn,
      ISA.OP_ldind,
      ISA.OP_ldindh,
      ISA.OP_ldindb,
      ISA.OP_stind,
      ISA.OP_stindh,
      ISA.OP_stindb,
      ISA.OP_loadi,
      ISA.OP_loadhi,
      ISA.OP_loadh2i,
      ISA.OP_loadh3i
    ).contains(io.op)
  ) {

    io.ctrl_alu_op2 := CTRL.ALU_OP2.imm
  }
    .elsewhen(
      VecInit(ISA.OP_addr, ISA.OP_subr, ISA.OP_andr, ISA.OP_orr, ISA.OP_xorr)
        .contains(io.op)

    ) {
      io.ctrl_alu_op2 := CTRL.ALU_OP2.reg
    }
    .otherwise {
      io.ctrl_alu_op2 := CTRL.ALU_OP2.imm
    }

  // Branch unit control
  when(io.op === ISA.OP_br) { io.ctrl_br := CTRL.BR.br }
    .elsewhen(io.op === ISA.OP_brz) { io.ctrl_br := CTRL.BR.brz }
    .elsewhen(io.op === ISA.OP_brnz) { io.ctrl_br := CTRL.BR.brnz }
    .elsewhen(io.op === ISA.OP_br) { io.ctrl_br := CTRL.BR.brp }
    .elsewhen(io.op === ISA.OP_br) { io.ctrl_br := CTRL.BR.brn }
    .otherwise { io.ctrl_br := CTRL.BR.nop }
}
