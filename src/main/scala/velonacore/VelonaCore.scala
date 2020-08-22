package velonamp.velonacore

import chisel3._
import velonamp.memory._
import chisel3.util._

import velonamp.common.ISA
import chisel3.util.log2Ceil

class VelonaCoreInterface extends Bundle {
  val soft_reset = Input(Bool())
  val reset_pc   = Input(UInt(ISA.REG_WIDTH.W))
  val reg_port   = new MemoryReadWriteInterface(ISA.REG_WIDTH, ISA.REG_BYTES)
  val data_mem_port =
    new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, ISA.REG_BYTES)
  val instr_mem_port =
    new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, ISA.INSTR_BYTES)
}

class VelonaCore extends Module {
  val io = IO(new VelonaCoreInterface())

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

  // ================================== Wires ==================================
  val instr_data        = io.instr_mem_port.data_in
  val reg_read_data     = io.reg_port.read.data.bits
  val mem_read_data     = io.data_mem_port.data_in
  val exec_control_flow = branch.io.do_branch || (decode.io.op === ISA.OP_jal)


  // ========================= Architetural registers ==========================
  io.reg_port.read.address := instr_data(7, 0)
  io.reg_port.read.data.ready := control.io.ctrl_reg_op === CTRL.MEM.rd

  io.reg_port.write.address := instr_data(7, 0)
  io.reg_port.write.data.valid := control.io.ctrl_reg_op === CTRL.MEM.wr
  io.reg_port.write.data.bits := Mux(
    decode.io.op === ISA.OP_jal,
    alu.io.out.asUInt(),
    acc_reg
  )

  io.reg_port.write.mask := VecInit(
    Seq.fill(ISA.REG_BYTES)(1.B)
  ) // Always write full word

  // =============================== Data memory ===============================
  io.data_mem_port.address := alu.io.out.asUInt()
  io.data_mem_port.op.bits := MemoryExclusiveReadWriteInterface.op_rd
  io.data_mem_port.op.valid := 0.B
  io.data_mem_port.data_out := acc_reg
  when(control.io.ctrl_mem_op =/= CTRL.MEM.nop) {
    io.data_mem_port.op.valid := 1.B
    io.data_mem_port.op.bits := Mux(control.io.ctrl_mem_op === CTRL.MEM.rd,
      MemoryExclusiveReadWriteInterface.op_rd,
      MemoryExclusiveReadWriteInterface.op_wr
    )
  }

  io.data_mem_port.mask := VecInit(Seq.fill(ISA.REG_BYTES)(0.B))
  switch(control.io.ctrl_mem_size) {
    is(CTRL.MEM_SIZE.byte) {
      io.data_mem_port.mask := VecInit(Seq(1.B, 0.B, 0.B, 0.B))
    }
    is(CTRL.MEM_SIZE.half) {
      io.data_mem_port.mask := VecInit(Seq(1.B, 1.B, 0.B, 0.B))
    }
    is(CTRL.MEM_SIZE.word) {
      io.data_mem_port.mask := VecInit(Seq(1.B, 1.B, 1.B, 1.B))
    }
  }

  // =========================== Instruction memory ============================
  io.instr_mem_port.mask := VecInit(Seq.fill(ISA.INSTR_BYTES)(1.B))
  io.instr_mem_port.address := pc_reg
  io.instr_mem_port.op.bits := MemoryExclusiveReadWriteInterface.op_rd
  io.instr_mem_port.op.valid := true.B
  io.instr_mem_port.data_out := DontCare

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
  state.io.data_mem.req := io.data_mem_port.op.valid
  state.io.data_mem.valid := io.data_mem_port.op.ready

  state.io.instr_mem.req := io.instr_mem_port.op.valid
  state.io.instr_mem.valid := io.instr_mem_port.op.ready

  state.io.reg.req := io.reg_port.read.data.ready || io.reg_port.write.data.valid
  state.io.reg.valid := io.reg_port.read.data.valid || io.reg_port.write.data.ready

  // ALU
  alu.io.ctrl_alu := control.io.ctrl_alu

  alu.io.op1 := addr_reg.asSInt
  switch(control.io.ctrl_alu_op1) {
    is(CTRL.ALU_OP1.acc) { alu.io.op1 := acc_reg.asSInt }
    is(CTRL.ALU_OP1.addr) { alu.io.op1 := addr_reg.asSInt }
    is(CTRL.ALU_OP1.pc) { alu.io.op1 := pc_reg.asSInt }
  }

  alu.io.op2 := reg_read_data.asSInt
  switch(control.io.ctrl_alu_op2) {
    is(CTRL.ALU_OP2.imm) { alu.io.op2 := immediate.io.imm.asSInt }
    is(CTRL.ALU_OP2.reg) { alu.io.op2 := reg_read_data.asSInt }
  }

  // Program counter source selection
  when(io.soft_reset) {
    pc_reg := io.reset_pc
  }.elsewhen(state.io.continue) {
    when(branch.io.do_branch) {
      pc_reg := alu.io.out.asUInt()
    }.elsewhen(decode.io.op === ISA.OP_jal) {
      pc_reg := acc_reg
    }.otherwise {
      pc_reg := pc_reg + ISA.INSTR_BYTES.U
    }
  }

  // Accumulator source selection
  switch(control.io.ctrl_acc) {
    is(CTRL.ACC_SRC.alu) { acc_reg := alu.io.out.asUInt }
    is(CTRL.ACC_SRC.reg) { acc_reg := reg_read_data }
    is(CTRL.ACC_SRC.mem) { acc_reg := mem_read_data }
    is(CTRL.ACC_SRC.acc) { acc_reg := acc_reg }
  }

  // Address register source selection
  when(decode.io.op === ISA.OP_ldaddr) { addr_reg := reg_read_data }
}

object VelonaCore extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaCore())
}
