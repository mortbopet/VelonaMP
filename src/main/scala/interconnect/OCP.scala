package velonamp.interconnect

import chisel3._
import chisel3.util.{Decoupled, Arbiter}

import velonamp.common.ISA

object OCP {
  object MCmd {
    def idle  = 0
    def write = 1
    def read  = 2
  }
  object SResp {
    def none = 0
    def dva = 1
    def fail = 2
    def err = 3
  }
}

class OCPMasterLine extends Bundle {
  val mAddr      = UInt(ISA.REG_WIDTH.W)
  val mCmd       = UInt(3.W)
  val mData      = UInt(ISA.REG_WIDTH.W)
  val mDataValid = UInt(ISA.REG_WIDTH.W)
}

class OCPSlaveLine extends Bundle {
  val sData      = UInt(ISA.REG_WIDTH.W)
  val sResp      = UInt(2.W)
  val sCmdAccept = Bool()
}

class OCPInterface extends Bundle {
  val master = Flipped(Decoupled(new OCPMasterLine()))
  val slave  = Decoupled(new OCPSlaveLine())
}
