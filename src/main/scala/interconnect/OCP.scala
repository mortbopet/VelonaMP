package velonamp.interconnect

import chisel3._
import chisel3.util.{Decoupled, Arbiter}

import velonamp.common.ISA

object OCP {
  def BUS_DATA_WIDTH = ISA.REG_WIDTH
  def BUS_DATA_BYTES = ISA.REG_BYTES
  object MCmd {
    def idle  = 0
    def write = 1
    def read  = 2
  }
  object SResp {
    def none = 0
    def dva  = 1 // Data valid/available
    def fail = 2
    def err  = 3
  }
}

class OCPMasterLine extends Bundle {
  val mAddr   = UInt(ISA.REG_WIDTH.W)
  val mCmd    = UInt(3.W)
  val mData   = UInt(ISA.REG_WIDTH.W)
  val mByteEn = Vec(ISA.REG_BYTES, Bool())
}

class OCPSlaveLine extends Bundle {
  val sData = UInt(ISA.REG_WIDTH.W)
  val sResp = UInt(2.W)
}

/**
  * An OCP interface.
  * The ready/valid handshaking on the master/slave line shall be used for
  * hardware to check whether they are currently being arbitrated on the bus.
  * After this is confirmed, the OCP master/slave lines may be asserted to
  * perform a transaction between the (now arbited) master and slave.
  *
  * @param (master/slave).valid: Assert for bus request
  * @param (master/slave).ready: asserted when bus granted
  */
class OCPMasterInterface extends Bundle {
  val master = Decoupled(new OCPMasterLine())
  val slave  = Input(new OCPSlaveLine())
}

class OCPSlaveInterface extends Bundle {
  val slave  = Decoupled(new OCPSlaveLine())
  val master = Input(new OCPMasterLine())
}
