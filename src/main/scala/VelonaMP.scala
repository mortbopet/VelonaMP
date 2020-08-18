package velonamp

import chisel3._

import velonamp.velonacore._
import velonamp.memory._
import velonamp.peripherals.{Loader, UART_rx}
import velonamp.interconnect._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.common._

class VelonaMP extends Module {
  val io = IO(new Bundle {
    // Resets the entire multiprocessor. Todo: Move loader out of VelonaMP
    // and attach system reset to this pin
    // val global_reset = Input(Bool())

    val uart_rx = Input(Bool())
    val dummy_out   = Output(UInt())
  })

  val core = Module(new VelonaCore())
  val loader = Module(new Loader())
  val uart_rx =  Module(new UART_rx(velonamp.util.LOADER_UART_BAUD, velonamp.util.F_HZ))

  val ocp_bus = Module(new OCPBus(2, 2))

  val instr_mem = Module(new OCPRWMemory(0, ISA.REG_WIDTH, ISA.REG_BYTES, 1024))
  val data_mem  = Module(new OCPRWMemory(0x1000, ISA.REG_WIDTH, ISA.REG_BYTES, 1024))
  val reg_mem   = Module(new RWMemory(ISA.REG_WIDTH, ISA.REG_BYTES, 256))

  core.io.soft_reset := loader.io.system_reset
  core.io.reset_pc := velonamp.util.MASTER_INIT_PC.U

  core.io.data_mem_port <> data_mem.io
  core.io.reg_port <> reg_mem.io
  core.io.instr_mem_port <> instr_mem.io.read

  uart_rx.io.rx := io.uart_rx
  loader.io.data_in <> uart_rx.io.data_out
  instr_mem.io.write <> loader.io.mem_port

  io.dummy_out := data_mem.io.read.data.bits
}

object VelonaMP extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaMP())
}

class VelonaMPTester(c: VelonaMP, baud : Int, freq : Int) extends PeekPokeTester(c) {
  val baud_cycles = freq / baud
  def tx(bit : Int) {
      poke(c.io.uart_rx, bit)
      step(baud_cycles)
  }
  def intToBitSeq(v : Int) = (0 until 8).map{ i => (v >> i) & 1}

  val bytes = List(
        intToBitSeq(2), // Select "Load" function
        intToBitSeq(1), //Seq(1, 0, 0, 0, 0, 0, 0 ,0 ), // # sections
        intToBitSeq(0), // section start B0
        intToBitSeq(0), // section start B1
        intToBitSeq(0), // section start B2
        intToBitSeq(0), // section start B2
        intToBitSeq(2), // # bytes B0
        intToBitSeq(0), // # bytes B0
        intToBitSeq(0), // # bytes B0
        intToBitSeq(0), // # bytes B0
        intToBitSeq(5),// data bytes B0 (immediate)
        intToBitSeq(9),// data bytes B1 (ADDI)
        intToBitSeq(1) // Select "Reset" function
    )

    for (byte <- bytes) {
        // Start bit
        tx(0)

        // Data
        for (bit <- byte) {
            tx(bit)
        }

        // Stop bits
        tx(1)
        tx(1)
    }

    step(baud_cycles * 10)
}

class VelonaMPSpec extends ChiselFlatSpec {
  "VelonaMP" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new VelonaMP()) { c =>
      new VelonaMPTester(c, velonamp.util.LOADER_UART_BAUD, velonamp.util.F_HZ)
    } should be(true)
  }
}