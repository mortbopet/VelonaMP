package velonamp.interconnect

import chisel3._
import chisel3.util._

import velonamp.interconnect._

trait OCPSlave extends MultiIOModule {
  val ocp_interface = IO(new OCPSlaveInterface())

  def ocpStart(): UInt
  def ocpEnd(): UInt

  val accessIsInThisAddressRange = Wire(Bool())

  accessIsInThisAddressRange := 0.B
  when(
    ocpStart() <= ocp_interface.master.mAddr &&
      ocp_interface.master.mAddr < ocpEnd()
  ) {
    accessIsInThisAddressRange := 1.B
  }

  ocp_interface.slave.valid := accessIsInThisAddressRange
}

