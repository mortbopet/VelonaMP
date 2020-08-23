package velonamp

import chisel3._

import velonamp.util._
import velonamp.common._
import velonamp.memory._
import velonamp.interconnect._
import velonamp.peripherals._

import chisel3.util.switch

/**
  * Top file for the Velona multiprocessor system
  */
class VelonaTop extends Module {
  val io = IO(new Bundle {
    val uart_rx = Input(Bool())
    val leds = Output(Vec(N_LEDS, Bool()))
    val switches = Input(Vec(N_SWITHCES, Bool()))
    val dummy_out = Output(UInt())
  })

  // ========================= Entity instantiation =========================
  // Multiprocessor system
  val velonamp = Module(new VelonaMP(N_CORES))

  // Memories
  val instr_mem = Module(new OCPRWMemory(ADDRESS_IMEM, ISA.REG_WIDTH, ISA.REG_BYTES, 1024))
  val data_mem = Module(new OCPRWMemory(ADDRESS_DMEM, ISA.REG_WIDTH, ISA.REG_BYTES, 1024))

  // Peripherals
  val loader = Module(new Loader())
  val uart_rx = Module(new UART_rx(LOADER_UART_BAUD, F_HZ))
  val leds = Module(new LEDs(ADDRESS_LED))
  val switches = Module(new Switches(ADDRESS_LED))

  // Interconnect
  val bus_masters = List(
    velonamp.io.dmem_interface,
    velonamp.io.imem_interface,
    loader.io.ocp_interface
  )

  val bus_slaves = List(
    instr_mem.ocp_interface,
    data_mem.ocp_interface,
    leds.ocp_interface,
    switches.ocp_interface
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

  // Peripherals
  loader.io.data_in <> uart_rx.io.data_out
  velonamp.io.global_reset := loader.io.system_reset
  uart_rx.io.rx := io.uart_rx
  io.leds := leds.leds
  switches.switches := io.switches
}

object VelonaTop extends App {
  chisel3.Driver.execute(Array[String](), () => new VelonaTop())
}
