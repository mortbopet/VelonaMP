package velonamp.peripherals

import chisel3._
import chisel3.util.{BitPat, Enum}

import velonamp.util._
import velonamp.interconnect._
import velonamp.interconnect.OCPSlave

class LEDs(val address: Int) extends OCPSlave {
  val leds = IO(Output(Vec(N_LEDS, Bool())))

  def ocpStart(): UInt = address.U
  def ocpEnd(): UInt   = (address + 4).U

  val led_reg = RegInit(VecInit(Seq.fill(N_LEDS) { 0.B }))
  leds := led_reg

  ocp_interface.slave.bits.sResp := 0.B
  ocp_interface.slave.bits.sData := DontCare
  when(accessIsInThisAddressRange) {
    ocp_interface.slave.bits.sResp := 1.B
    when(ocp_interface.master.mCmd === OCP.MCmd.write.U) {
      led_reg := ocp_interface.master.mData(N_LEDS - 1, 0).asBools()
    }.otherwise {
      ocp_interface.slave.bits.sData := led_reg.asUInt()
    }
  }
}
