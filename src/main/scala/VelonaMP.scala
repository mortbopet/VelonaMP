package velonamp

import chisel3._

import velonamp.velonacore._
import velonamp.memory.{RWMemory, ROMemory}

import velonamp.common._

class VelonaMP extends Module {
  val io = IO(new Bundle {
    val instr_addr  = Input(UInt(ISA.REG_WIDTH.W))
    val instr_valid = Input(Bool())
    val instr_data  = Input(UInt(ISA.INSTR_WIDTH.W))
    val dummy_out   = Output(UInt())
  })

  val core = Module(new VelonaCore())

  val instr_mem = Module(new RWMemory(ISA.REG_WIDTH, ISA.INSTR_WIDTH, 1024))
  val data_mem  = Module(new RWMemory(ISA.REG_WIDTH, ISA.REG_WIDTH, 1024))
  val reg_mem   = Module(new RWMemory(ISA.REG_WIDTH, ISA.REG_WIDTH, 256))

  core.io.data_mem_port <> data_mem.io
  core.io.reg_port <> reg_mem.io
  core.io.instr_mem_port <> instr_mem.io.read

  instr_mem.io.write.address := io.instr_addr
  instr_mem.io.write.data.valid := io.instr_valid
  instr_mem.io.write.data.bits := io.instr_data

  io.dummy_out := data_mem.io.read.data.bits
}

object VelonaMP extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaMP())
}
