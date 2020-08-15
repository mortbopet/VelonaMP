package velonamp.velonacore

import chisel3._
import velonamp.memory._
import chisel3.util._

import velonamp.common.ISA
import chisel3.util.log2Ceil

class VelonaCore extends Module {
  val io = IO(new Bundle {
    val data_mem =
      Flipped(new MemoryReadWriteInterface(ISA.REG_WIDTH, ISA.REG_WIDTH))
    val instr_data_mem =
      Flipped(new MemoryReadInterface(ISA.REG_WIDTH, ISA.INSTR_WIDTH))
    val reg_mem =
      Flipped(new MemoryReadWriteInterface(ISA.REG_WIDTH, ISA.REG_WIDTH))
  })

  // Module instantiations
  val branch    = Module(new Branch())
  val decode    = Module(new Decode())
  val control   = Module(new Control())
  val immediate = Module(new Immediate())
  val alu       = Module(new ALU())
  val state     = Module(new State())

  // Registers
  val addr_reg = RegInit(0.U(ISA.REG_WIDTH.W))
  val acc_reg  = RegInit(0.U(ISA.REG_WIDTH.W))
  val pc_reg   = RegInit(0.U(ISA.REG_WIDTH.W))

  // Wires
  val instr_data    = io.instr_data_mem.data_out.bits
  val reg_read_data = io.reg_mem.data_out.bits
  val mem_read_data = io.data_mem.data_out.bits

  // Architectural registers
  io.reg_mem.address := io.instr_data_mem.data_out.bits(7, 0)
  io.reg_mem.data_out.ready := control.io.ctrl_reg_op === CTRL.MEM.rd
  io.reg_mem.data_in.valid := control.io.ctrl_reg_op === CTRL.MEM.wr

  io.reg_mem.data_in.bits := Mux(
    decode.io.op === ISA.OP_jal,
    alu.io.out.asUInt(),
    acc_reg
  )

  // Data memory
  io.data_mem.address := alu.io.out.asUInt()
  io.data_mem.data_out.ready := control.io.ctrl_mem_op === CTRL.MEM.rd
  io.data_mem.data_in.valid := control.io.ctrl_mem_op === CTRL.MEM.wr
  io.data_mem.data_in.bits := acc_reg

  // instr_datauction memory
  io.instr_data_mem.address := pc_reg
  io.instr_data_mem.data_out.ready := true.B

  // Decode unit
  decode.io.instr_data := instr_data

  // Control unit
  control.io.op := decode.io.op

  // Branch unit
  branch.io.ctrl_br := control.io.ctrl_br
  branch.io.acc := acc_reg

  // Immediate unit
  immediate.io.ctrl_imm := control.io.ctrl_imm
  immediate.io.instr_data := instr_data

  // State unit
  state.io.data_mem.req := io.data_mem.data_out.ready || io.data_mem.data_in.ready
  state.io.data_mem.valid := io.data_mem.data_out.valid || io.data_mem.data_in.valid
  state.io.reg.req := io.reg_mem.data_out.ready || io.reg_mem.data_in.ready
  state.io.reg.valid := io.reg_mem.data_out.valid || io.reg_mem.data_in.valid

  // ALU
  alu.io.ctrl_alu := control.io.ctrl_alu
  alu.io.op1 := acc_reg.asSInt
  switch(control.io.ctrl_alu_op1) {
    is(CTRL.ALU_OP1.acc) { alu.io.op1 := acc_reg.asSInt }
    is(CTRL.ALU_OP1.addr) { alu.io.op1 := addr_reg.asSInt }
    is(CTRL.ALU_OP1.pc) { alu.io.op1 := pc_reg.asSInt }
  }

  alu.io.op2 := immediate.io.imm.asSInt
  switch(control.io.ctrl_alu_op2) {
    is(CTRL.ALU_OP2.imm) { alu.io.op2 := immediate.io.imm.asSInt }
    is(CTRL.ALU_OP2.reg) { alu.io.op2 := addr_reg.asSInt }
  }

  // Program counter source selection
  when(branch.io.do_branch) {
    pc_reg := alu.io.out.asUInt()
  }.elsewhen(decode.io.op === ISA.OP_jal) {
    pc_reg := acc_reg
  }.otherwise {
    pc_reg := pc_reg + log2Ceil(ISA.INSTR_WIDTH).U
  }

  // Accumulator source selection
  switch(control.io.ctrl_acc) {
    is(CTRL.ACC_SRC.alu) { acc_reg := alu.io.out.asUInt }
    is(CTRL.ACC_SRC.reg) { acc_reg := reg_read_data }
    is(CTRL.ACC_SRC.mem) { acc_reg := mem_read_data }
  }

  // Address register source selection
  when(decode.io.op === ISA.OP_ldaddr) { addr_reg := reg_read_data }
}

object VelonaCore extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaCore())
}
