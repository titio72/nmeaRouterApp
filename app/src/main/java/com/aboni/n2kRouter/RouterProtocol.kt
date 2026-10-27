package com.aboni.n2kRouter

import com.aboni.marine.link.Inbound
import com.aboni.marine.link.Packet
import com.aboni.marine.link.Protocol
import com.aboni.marine.link.Replies
import com.aboni.marine.link.ble.BleProfile
import com.aboni.marine.link.ble.PairingPolicy
import java.util.UUID

/** The Router's side of the link: which channel carries what. These numbers are ours, not the device's. */
object RouterChannels {
    const val CONF = 1
    const val DATA = 2
    const val CMD = 3
    const val HEARTBEAT = 4
}

/** One ASCII write to a device characteristic (`N...`, `T...`, `H...`, `t...`, heartbeat `h`). */
data class RouterCommand(val channel: Int, val payload: String) {
    companion object {
        val HEARTBEAT = RouterCommand(RouterChannels.HEARTBEAT, "h")
    }
}

/** What the device reported. [data] and [conf] are updated in place; [revision] changes so the flow emits. */
data class RouterState(val data: Data, val conf: Conf, val revision: Long)

/** [data] and [conf] are the instances the app keeps and hands to its screens; decoding updates them in place. */
class RouterProtocol(private val data: Data = Data(), private val conf: Conf = Conf()) :
    Protocol<RouterState, RouterCommand, Nothing> {

    override fun decode(p: Packet, current: RouterState?): Inbound<RouterState, Nothing>? {
        val state = current ?: RouterState(data, conf, 0)
        when (p.channel) {
            RouterChannels.CONF -> state.conf.copyFrom(p.bytes)
            RouterChannels.DATA -> state.data.parse(p.bytes)
            else -> return null
        }
        return Inbound.Data(state.copy(revision = state.revision + 1))
    }

    override fun encode(seq: Int, cmd: RouterCommand): Packet = Packet(cmd.channel, cmd.payload.toByteArray(Charsets.UTF_8))

    override val replies: Replies<Nothing>? = null
}

private fun uuid(s: String) = UUID.fromString(s)

/** The router's BLE contract. The UUIDs must match the firmware (n2k_router_arduino). */
val ROUTER_PROFILE = BleProfile(
    service = uuid("4fafc201-1fb5-459e-8fcc-c5c9c331914b"),
    notify = mapOf(uuid("55da66c7-801f-498d-b652-c57cb3f1b590") to RouterChannels.DATA),
    readOnConnect = mapOf(
        uuid("beb5483e-36e1-4688-b7f5-ea07361b26a8") to RouterChannels.CONF,
        uuid("55da66c7-801f-498d-b652-c57cb3f1b590") to RouterChannels.DATA,
    ),
    write = mapOf(
        RouterChannels.CMD to uuid("68ad1094-0989-4e22-9f21-4df7ef390803"),
        RouterChannels.HEARTBEAT to uuid("31a627d4-90cd-43df-8c0d-460e77fd294b"),
    ),
    mtu = 128,
    // Data is public; only commands need the passkey. The heartbeat characteristic needs no pairing.
    pairing = PairingPolicy.OnDemand,
    securedWrites = setOf(RouterChannels.CMD),
)
