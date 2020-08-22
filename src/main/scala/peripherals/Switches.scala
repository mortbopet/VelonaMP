package velonamp.peripherals

import chisel3._
import chisel3.util.{BitPat, Enum}

import velonamp.util._
import velonamp.interconnect._
import velonamp.interconnect.OCPSlave

class Switches(val address: Int) extends OCPSlave {
  val switches = IO(Input(Vec(N_SWITHCES, Bool())))

  def ocpStart(): UInt = address.U
  def ocpEnd(): UInt   = (address + 4).U

  val switches_reg = RegNext(switches)
  ocp_interface.slave.bits.sData := switches_reg.asUInt()
  ocp_interface.slave.bits.sResp := Mux(accessIsInThisAddressRange,
    OCP.SResp.dva.U, OCP.SResp.none.U)
}
