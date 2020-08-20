package velonamp.memory

import chisel3._
import velonamp.memory._
import chisel3.util._

import velonamp.common.ISA
import velonamp.interconnect._
import chisel3.util.log2Ceil

import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}


class CacheLine(val n_blocks : Int, val tag_bits : Int) extends Bundle {
    val dirty = Bool()
    val tag = UInt(tag_bits.W)
    val blocks = Vec(n_blocks, UInt(ISA.REG_WIDTH.W))
}

/** A simple direct-mapped cache.
 * @param n_lines: 2^(n_lines) cache lines
 * @param n_blocks: 2^(n_blocks) blocks in each cache line
 * @param read_only: If true, disables write interface
 *
 * @todo: Have a way to designated the cached address space to allow for
 * read/write-thru to hardware registers.
 */
class Cache(n_lines : Int, n_blocks : Int, read_only : Boolean = false) extends Module {
    val io = IO(new Bundle {
        val core_interface = Flipped(new MemoryExclusiveReadWriteInterface(ISA.REG_WIDTH, ISA.REG_BYTES))
        val host_interface = new OCPMasterInterface()
        val soft_reset = Input(Bool())
    })

    def genMask(n : Int) : Vec[Bool] = { VecInit(Seq.fill(n)(1.B)) }

    val s_ready :: s_readLine :: s_writebackLine :: Nil = Enum(3)
    val state = RegInit(s_ready)
    val blocksToStream = RegInit((n_blocks - 1).U)

    // Compute access constants
    val byteOffset = log2Ceil(ISA.REG_BYTES)
    val blockOffset = byteOffset + n_blocks
    val indexOffset = blockOffset + n_lines
    val tag_bits = ISA.REG_WIDTH - indexOffset

    // Instantiate actual cache hardware
    val cacheLines = Reg(Vec(n_lines, new CacheLine(n_blocks, tag_bits)))
    val validBits = RegInit(VecInit(Seq.fill(n_lines)(0.B))) // Chisel does not seem to like initialization values in Vector-instantiated bundles, so valid bits are kept outside of the CacheLine bundle st. we may zero-initialize

    // Compute access
    val blockIdx = (io.core_interface.address >> byteOffset) & genMask(log2Ceil(n_blocks)).asUInt()
    val lineIdx = (io.core_interface.address >> blockOffset) & genMask(log2Ceil(n_lines)).asUInt()
    val currentTag = (io.core_interface.address >> indexOffset) & genMask(tag_bits).asUInt()

    val currentLine = cacheLines(lineIdx)
    val cacheHit = currentLine.tag === currentTag && validBits(lineIdx)

    // Core <> Cache logic
    io.core_interface.op.ready := 0.B
    io.core_interface.data_in := DontCare
    when(!io.soft_reset && state === s_ready && io.core_interface.op.valid) {
        when(cacheHit) {
            when(io.core_interface.op.bits === MemoryExclusiveReadWriteInterface.op_wr) {
                currentLine.blocks(blockIdx) := io.core_interface.data_out
                currentLine.dirty := 1.B
            } .otherwise {
                io.core_interface.data_in := currentLine.blocks(blockIdx)
            }
            io.core_interface.op.ready := 1.B
        }. otherwise {
            // Get value from memory; writeback current line if dirty
            blocksToStream := (n_blocks - 1).U
            when(validBits(lineIdx) && currentLine.dirty) {
                state := s_writebackLine
            } .otherwise {
                state := s_readLine
            }
        }
    }

    io.host_interface.master.valid := 0.B
    io.host_interface.master.bits.mAddr := DontCare
    io.host_interface.master.bits.mCmd := OCP.MCmd.idle.U
    io.host_interface.master.bits.mByteEn := genMask(ISA.REG_BYTES) // Always read/write all bytes
    io.host_interface.master.bits.mData := currentLine.blocks(blocksToStream)
    when(!io.soft_reset && state =/= s_ready) {
        io.host_interface.master.valid := 1.B // Request bus access
        when(io.host_interface.master.ready) { // Wait for bus grant
            when(state === s_readLine) {
                io.host_interface.master.bits.mAddr := ((io.core_interface.address >> blockOffset) << blockOffset) + (blocksToStream << byteOffset)
                io.host_interface.master.bits.mCmd := OCP.MCmd.read.U

            } .elsewhen(state === s_writebackLine) {
                io.host_interface.master.bits.mAddr := (currentLine.tag << blockOffset) + (blocksToStream << byteOffset)
                io.host_interface.master.bits.mCmd := OCP.MCmd.write.U
            }

            when(io.host_interface.slave.sResp === OCP.SResp.dva.U) {
                blocksToStream := blocksToStream - 1.U
                when(state === s_readLine) {
                    currentLine.blocks(blocksToStream) := io.host_interface.slave.sData
                }
            }

            when(blocksToStream === 0.U) {
                // Current line written back to memory, now read requested line into cache
                when(state === s_writebackLine) {
                    state := s_readLine
                    blocksToStream := (n_blocks - 1).U
                } .otherwise {
                    currentLine.tag := currentTag
                    validBits(lineIdx) := 1.B
                    state := s_ready
                }
            }
        }
    }

    when(io.soft_reset) {
        validBits.foreach(_ := 0.B)
    }
}

class CacheTester(c: Cache) extends PeekPokeTester(c) {
    // Core interface
    poke(c.io.core_interface.address, 4)
    poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_rd)
    poke(c.io.core_interface.op.valid, 1)

    // Host interface
    poke(c.io.host_interface.master.ready, 1) // Bus grant
    poke(c.io.host_interface.slave.sData, 0)
    poke(c.io.host_interface.slave.sResp, OCP.SResp.dva)
    step(1)
    expect(c.io.core_interface.op.ready, 0, "Line should not be cached")
    step(4)
    expect(c.io.core_interface.op.ready, 1, "Line should now be available")


    poke(c.io.core_interface.address, 5)
    poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_wr)
    expect(c.io.core_interface.op.ready, 1, "Line already cached, write should go through")
    step(1)
    poke(c.io.host_interface.master.ready, 0) // Bus released



    poke(c.io.core_interface.address, 4 + 1 << 8)
    poke(c.io.core_interface.op.bits, MemoryExclusiveReadWriteInterface.op_rd)
    expect(c.io.core_interface.op.ready, 0, "address is not cached, but line is valid in cache. Line must be written back to memory")
    step(4)
    poke(c.io.host_interface.master.ready, 1) // Bus granted
    step(10)
}


class CacheSpec extends ChiselFlatSpec {
  "CacheSpec" should "pass" in {
    Driver.execute(Array("--generate-vcd-output", "on") ,()
        => new Cache(4, 4, false)) { c =>
      new CacheTester(c)
    } should be(true)
  }
}
