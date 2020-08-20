package velonamp

import chisel3._

import velonamp.velonacore._
import velonamp.memory._
import velonamp.interconnect._

import chisel3.util.{Decoupled, Arbiter}

import velonamp.common._

class VelonaMP(n_cores : Int) extends Module {
  val io = IO(new Bundle {
    // Resets the entire multiprocessor.
    val global_reset = Input(Bool())

    val imem_interface = new OCPMasterInterface()
    val dmem_interface = new OCPMasterInterface()
  })

  // Single core
  val reg_mem = Module(new RWMemory(ISA.REG_WIDTH, ISA.REG_BYTES, 256))
  val core = Module(new VelonaCore())
  val icache = Module(new Cache(4, 4, true))
  val dcache = Module(new Cache(4, 4))

  icache.io.host_interface <> io.imem_interface
  dcache.io.host_interface <> io.dmem_interface
  core.io.data_mem_port <> dcache.io.core_interface
  core.io.instr_mem_port <> icache.io.core_interface

  core.io.reg_port <> reg_mem.io

  core.io.soft_reset := io.global_reset
  icache.io.soft_reset := io.global_reset
  dcache.io.soft_reset := io.global_reset
  core.io.reset_pc := velonamp.util.MASTER_INIT_PC.U
}

object VelonaMP extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaMP(1))
}
