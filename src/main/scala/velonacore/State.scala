package velonamp.velonacore

import chisel3._
import chisel3.util._

class MemoryState extends Bundle {
  val req   = Input(Bool())
  val valid = Input(Bool())
}

class State extends Module {
  val io = IO(new Bundle {
    val soft_reset = Input(Bool())

    val reg = new MemoryState()
    val data_mem = new MemoryState()

    val if_do_branch = Input(Bool())

    val continue = Output(Bool())
  })

  val reg_ready = !io.reg.req || (io.reg.req && io.reg.valid)
  val data_mem_ready = !io.data_mem.req || (io.data_mem.req && io.data_mem.valid)

  io.continue := !io.if_do_branch && reg_ready && data_mem_ready
}
