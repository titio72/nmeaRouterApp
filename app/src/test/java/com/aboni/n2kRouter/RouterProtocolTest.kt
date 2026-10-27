package com.aboni.n2kRouter

import com.aboni.marine.link.Inbound
import com.aboni.marine.link.Packet
import com.aboni.marine.link.ble.PairingPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RouterProtocolTest {
    private val protocol = RouterProtocol()

    private fun putLE(data: ByteArray, offset: Int, size: Int, value: Long) {
        for (i in 0 until size) data[offset + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    /** Version 10 layout, as in DataParseTest: sog at 31 (x0.01), rpm at 35. */
    private fun payload(version: Int = 10, sog: Long = 550): ByteArray = ByteArray(80).also {
        it[0] = version.toByte()
        putLE(it, 31, 2, sog)
        putLE(it, 35, 2, 3000)
    }

    private fun decoded(p: Packet, current: RouterState? = null): RouterState =
        (protocol.decode(p, current) as Inbound.Data<*>).value as RouterState

    @Test
    fun dataPacketUpdatesData() {
        val s = decoded(Packet(RouterChannels.DATA, payload()))
        assertEquals(10, s.data.version)
        assertEquals(5.5, s.data.sog.value, 1e-9)
        assertEquals(3000L, s.data.rpm.value.toLong())
    }

    @Test
    fun legacyVersionIsAcceptedAndLeavesFieldsUntouched() {
        val s = decoded(Packet(RouterChannels.DATA, payload(version = 9)))
        assertEquals(9, s.data.version)
        assertFalse(s.data.sog.valid)
    }

    @Test
    fun confPacketUpdatesConf() {
        val s = decoded(Packet(RouterChannels.CONF, "10100000000".toByteArray()))
        assertTrue(s.conf.bGPS)
        assertFalse(s.conf.bDHT)
        assertTrue(s.conf.bBME)
    }

    @Test
    fun eachDecodeReturnsANewStateThatSharesTheDataAndConf() {
        val first = decoded(Packet(RouterChannels.DATA, payload(sog = 100)))
        val second = decoded(Packet(RouterChannels.DATA, payload(sog = 200)), first)
        assertNotSame(first, second)
        assertEquals(first.revision + 1, second.revision)
        assertSame(first.data, second.data)
        assertSame(first.conf, second.conf)
        assertEquals(2.0, second.data.sog.value, 1e-9)
    }

    @Test
    fun dataAndConfAccumulateInOneState() {
        val a = decoded(Packet(RouterChannels.CONF, "10000000000".toByteArray()))
        val b = decoded(Packet(RouterChannels.DATA, payload()), a)
        assertTrue(b.conf.bGPS)
        assertEquals(10, b.data.version)
    }

    @Test
    fun unknownChannelIsIgnored() {
        assertNull(protocol.decode(Packet(99, byteArrayOf(1)), null))
    }

    @Test
    fun encodeKeepsPayloadAndChannel() {
        val p = protocol.encode(0, RouterCommand(RouterChannels.CMD, "T1200"))
        assertEquals(RouterChannels.CMD, p.channel)
        assertArrayEquals("T1200".toByteArray(Charsets.UTF_8), p.bytes)
    }

    @Test
    fun heartbeatIsChannelFourWithPayloadH() {
        assertEquals(RouterCommand(RouterChannels.HEARTBEAT, "h"), RouterCommand.HEARTBEAT)
        assertEquals(4, RouterChannels.HEARTBEAT)
    }

    @Test
    fun theStateUsesTheInstancesTheAppPassedIn() {
        val data = Data(); val conf = Conf()
        val s = (RouterProtocol(data, conf).decode(Packet(RouterChannels.DATA, payload()), null) as Inbound.Data<*>).value as RouterState
        assertSame(data, s.data)
        assertSame(conf, s.conf)
        assertEquals(10, data.version)
    }

    @Test
    fun deviceDoesNotReplyToCommands() {
        assertNull(protocol.replies)
    }

    @Test
    fun profileUsesTheContractUuids() {
        val p = ROUTER_PROFILE
        val conf = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val data = UUID.fromString("55da66c7-801f-498d-b652-c57cb3f1b590")
        val cmd = UUID.fromString("68ad1094-0989-4e22-9f21-4df7ef390803")
        val hb = UUID.fromString("31a627d4-90cd-43df-8c0d-460e77fd294b")
        assertEquals(UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b"), p.service)
        assertEquals(mapOf(data to RouterChannels.DATA), p.notify)
        assertEquals(mapOf(conf to RouterChannels.CONF, data to RouterChannels.DATA), p.readOnConnect)
        assertEquals(mapOf(RouterChannels.CMD to cmd, RouterChannels.HEARTBEAT to hb), p.write)
        assertEquals(128, p.mtu)
    }

    @Test
    fun profileIsOnDemandWithOnlyCmdSecured() {
        assertEquals(PairingPolicy.OnDemand, ROUTER_PROFILE.pairing)
        assertEquals(setOf(RouterChannels.CMD), ROUTER_PROFILE.securedWrites)
        assertEquals(15_000L, ROUTER_PROFILE.writeTimeoutMs)
    }
}
