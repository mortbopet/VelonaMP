package velonamp

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.assembler._
import velonamp.util._

class VelonaTopTester(c: VelonaTop, baud : Int, freq : Int) extends PeekPokeTester(c) {
  val baud_cycles = freq / baud
  def tx(bit : Int) {
      poke(c.io.uart_rx, bit)
      step(baud_cycles)
  }
  def intToBitSeq(v : Int) = (0 until 8).map{ i => (v >> i) & 1}

  val program = LerosAssembler.assembleProgram(List(
      // Load data memory address location
      "loadi 0x" + (ADDRESS_DMEM & 0xFF).toHexString,
      "loadhi 0x" + ((ADDRESS_DMEM >> 8) & 0xFF).toHexString,
      "loadh2i 0x" + ((ADDRESS_DMEM >> 16) & 0xFF).toHexString,
      "loadh3i 0x" + ((ADDRESS_DMEM >> 24) & 0xFF).toHexString,
      "store r0",
      "ldaddr",
      // Store 0x0 to data mem
      "loadi 0x0",
      "stind 0x0",
      // Loop:
      "ldind 0x0",
      "addi 0x1",
      "stind 0x0",
      "loadi 0xCD", // Set accumulator to some identifiable value
      "br -8"
    ).mkString("\n"))

  val bytes = List(
        LerosAssembler.intToByteSeq(2, 1), // Select "Load" function
        LerosAssembler.intToByteSeq(1, 1), // # sections
        LerosAssembler.intToByteSeq(0, 4), // section start B0
        LerosAssembler.intToByteSeq(program.length, 4), // # bytes B0
        program
    ).reduce((l , r) => l ++ r)

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
