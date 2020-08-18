package velonamp.interconnect

import chisel3._
import chisel3.util._

import velonamp.interconnect._

trait OCPSlave extends MultiIOModule {
  val ocp_interface = IO(new OCPInterface())

  def ocpStart(): UInt
  def ocpEnd(): UInt

  ocp_interface.slave.valid := 0.B
  when(ocp_interface.master.valid) {
    when(
      ocpStart() <= ocp_interface.master.bits.mAddr &&
        ocp_interface.master.bits.mAddr < ocpEnd()
    ) {
      ocp_interface.slave.valid := 1.B
    }
  }
}
