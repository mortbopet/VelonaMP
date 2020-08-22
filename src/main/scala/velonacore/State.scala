package velonamp.velonacore

import chisel3._
import chisel3.util._

class MemoryState extends Bundle {
  val req   = Input(Bool())
  val valid = Input(Bool())
}

class State extends Module {
  val io = IO(new Bundle {
    val reg = new MemoryState()
    val data_mem = new MemoryState()
    val instr_mem = new MemoryState()

    val continue = Output(Bool())
  })

  val reg_ready = !io.reg.req || (io.reg.req && io.reg.valid)
  val data_mem_ready = !io.data_mem.req || (io.data_mem.req && io.data_mem.valid)
  val instr_mem_ready = !io.instr_mem.req || (io.instr_mem.req && io.instr_mem.valid)

  io.continue := reg_ready && data_mem_ready && instr_mem_ready
}
