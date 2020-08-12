package velonamp.velonacore

import chisel3._
import velonamp.memory.PodMemory


class VelonaCore extends Module {
    val io = IO(new Bundle {
        val instr = Input(UInt(16.W))
        val out = Output(ISA.Op())
        val imm = Output(UInt(ISA.REG_WIDTH.W))
    })

    val decode = Module(new Decode())
    decode.io.instr := io.instr
    io.out := decode.io.op

    val control = Module(new Control())
    control.io.op := decode.io.op

    val immediate = Module(new Immediate())
    immediate.io.instr := io.instr;
    io.imm := immediate.io.imm;
    immediate.io.op := control.io.ctrl_imm

    val mem = Module(new PodMemory())
}

object VelonaCore extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaCore())
}