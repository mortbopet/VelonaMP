package velonamp.velonacore

import velonamp.common.ISA
import velonamp.velonacore.CTRL.BR

import chisel3._
import chisel3.util._

class Branch extends Module {
  val io = IO(new Bundle {
    val acc = Input(UInt(ISA.REG_WIDTH.W))
    val ctrl_br = Input(CTRL.BR())
    val do_branch = Output(Bool())
  })

  when(
    io.ctrl_br === CTRL.BR.br ||
    (io.ctrl_br === CTRL.BR.brz && io.acc === 0.U) ||
    (io.ctrl_br === CTRL.BR.brp && io.acc.asSInt >= 0.S) ||
    (io.ctrl_br === CTRL.BR.brn && io.acc.asSInt < 0.S) ||
    (io.ctrl_br === CTRL.BR.brnz && io.acc =/= 0.U)) {
      io.do_branch := true.B
  } .otherwise {
    io.do_branch := false.B
  }
}
