package velonamp.memory

import chisel3._
import chisel3.util._

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import velonamp.util._
import velonamp.common.{ABI, ISA}

import scala.collection.mutable

class MemoryReadInterface(val w_addr: Int, val bytes_data : Int) extends Bundle {
  val address = Input(UInt(w_addr.W))
  val data    = Decoupled(UInt((bytes_data*8).W))
}

class MemoryWriteInterface(val w_addr: Int, val bytes_data : Int) extends Bundle {
  val address = Input(UInt(w_addr.W))
  val mask = Input(Vec(bytes_data, Bool()))
  val data    = Flipped(Decoupled(UInt((bytes_data*8).W)))
}

class MemoryReadWriteInterface(val w_addr: Int, val w_data: Int)
    extends Bundle {
  val read  = new MemoryReadInterface(w_addr, w_data)
  val write = new MemoryWriteInterface(w_addr, w_data)
}

class RWMemory(w_addr: Int, bytes_data: Int, size: Int) extends Module {
  val io = IO(new MemoryReadWriteInterface(w_addr, bytes_data))
  val mem = SyncReadMem(size, Vec(bytes_data, UInt(8.W)))
  val writeToReadAddr = io.read.address === io.write.address && io.write.data.valid

  val doForwardWrite = RegNext(writeToReadAddr)
  val delayedWriteData = RegNext(io.write.data.bits)

  val inVec = Wire(Vec(bytes_data, UInt(8.W)))
  val readData = Wire(UInt((bytes_data * 8).W))

  // Split input word to bytes
  for(i <- 0 until bytes_data) {
    inVec(i) := io.write.data.bits((i+1)*8 - 1, i*8)
  }
  // Merge output bytes to word
  readData := mem.read(io.read.address).asUInt()

  io.read.data.bits := Mux(doForwardWrite, delayedWriteData, readData)
  io.read.data.valid := true.B
  io.write.data.ready := false.B
  when(io.write.data.valid) {
    mem.write(io.write.address, inVec, io.write.mask)
  }
  io.write.data.ready := true.B
}

class RWMemoryTester(c: RWMemory) extends PeekPokeTester(c) {
  def toPokeableValue[T <: Int](x : T) = BigInt(x)
  val addr    = 42
  val value_1 = 123
  val value_2 = value_1 + 1
  val mask = Array(1,1,1,1) map toPokeableValue


  poke(c.io.write.address, addr)
  poke(c.io.read.address, addr)

  // Write
  poke(c.io.write.data.bits, value_1)
  poke(c.io.write.mask, mask)

  poke(c.io.write.data.valid, true)
  step(1)
  poke(c.io.write.data.valid, false)

  // read
  poke(c.io.read.data.ready, true)
  step(1)
  expect(c.io.read.data.bits, value_1)

  // Simultanious read/write
  poke(c.io.read.data.ready, true)
  poke(c.io.write.data.valid, true)
  poke(c.io.write.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
  step(1)
  expect(c.io.read.data.bits, value_2)
}

class PodMemorySpec extends ChiselFlatSpec {
    "PodMemory" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new RWMemory(ISA.REG_WIDTH, ISA.REG_BYTES, 256)) { c =>
      new RWMemoryTester(c)
    } should be(true)
  }
}
