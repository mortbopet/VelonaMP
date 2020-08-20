package velonamp

import chisel3._

import velonamp.util._
import velonamp.common._
import velonamp.memory._
import velonamp.interconnect._
import velonamp.peripherals._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

/**
  * Top file for the Velona multiprocessor system
  */
class VelonaTop extends Module {
  val io = IO(new Bundle {
    // Inputs
    val uart_rx = Input(Bool())

    // Outputs
    val dummy_out = Output(UInt())
  })

  // ========================= Entity instantiation =========================
  // Multiprocessor system
  val velonamp = Module(new VelonaMP(N_CORES))

  // Memories
  val instr_mem = Module(new OCPRWMemory(0, ISA.REG_WIDTH, ISA.REG_BYTES, 1024))
  val data_mem = Module(
    new OCPRWMemory(0x1000, ISA.REG_WIDTH, ISA.REG_BYTES, 1024)
  )

  // Peripherals
  val loader = Module(new Loader())
  val uart_rx = Module(
    new UART_rx(LOADER_UART_BAUD, F_HZ)
  )

  // Interconnect
  val bus_masters = List(
    velonamp.io.dmem_interface,
    velonamp.io.imem_interface,
    loader.io.ocp_interface
  )

  val bus_slaves = List(
    instr_mem.ocp_interface,
    data_mem.ocp_interface
  )

  val ocp_bus = Module(
    new OCPBus(
      bus_masters.length,
      bus_slaves.length
    )
  )

  // ============================= Connectivity =============================

  // Connect bus masters
  bus_masters.zipWithIndex.foreach {
    case (interface, i) =>
      ocp_bus.io.masters(i) <> interface
  }

  // Connect bus slaves
  bus_slaves.zipWithIndex.foreach {
    case (interface, i) =>
      ocp_bus.io.slaves(i) <> interface
  }

  io.dummy_out := data_mem.ocp_interface.slave.bits.sData

  // Loader
  loader.io.data_in <> uart_rx.io.data_out
  velonamp.io.global_reset := loader.io.system_reset

  // UART
  uart_rx.io.rx := io.uart_rx
}

object VelonaTop extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaTop())
}

class VelonaTopTester(c: VelonaTop, baud : Int, freq : Int) extends PeekPokeTester(c) {
  val baud_cycles = freq / baud
  def tx(bit : Int) {
      poke(c.io.uart_rx, bit)
      step(baud_cycles)
  }
  def intToBitSeq(v : Int) = (0 until 8).map{ i => (v >> i) & 1}

  val bytes = List(
        intToBitSeq(2), // Select "Load" function
        intToBitSeq(1), // # sections
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
        step((baud_cycles * 0.23).toInt)
    }

    step(baud_cycles * 20)
}

class VelonaTopSpec extends ChiselFlatSpec {
  "VelonaTop" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new VelonaTop) { c =>
      new VelonaTopTester(c, velonamp.util.LOADER_UART_BAUD, velonamp.util.F_HZ)
    } should be(true)
  }
}
