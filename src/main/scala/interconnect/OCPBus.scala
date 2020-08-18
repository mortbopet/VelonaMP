package velonamp.interconnect

import chisel3._
import chisel3.util.{Decoupled, Arbiter}

import velonamp.common.ISA

class OCPBus(n_masters: Int, n_slaves: Int) extends Module {
  val io = IO(new Bundle {
    val masters = Vec(n_masters, new OCPInterface())
    val slaves  = Vec(n_slaves, Flipped(new OCPInterface()))
  })

  val master_arbiter = Module(new Arbiter(new OCPMasterLine(), n_masters))
  val slave_arbiter  = Module(new Arbiter(new OCPSlaveLine(), n_slaves))

  io.slaves.foreach { _.master <> master_arbiter.io.out }
  io.masters.foreach { _.slave <> slave_arbiter.io.out }

  (master_arbiter.io.in zip io.masters).foreach{ case (l, r) => l <> r.master }
  (slave_arbiter.io.in zip io.slaves).foreach{ case (l, r) => l <> r.slave }
}