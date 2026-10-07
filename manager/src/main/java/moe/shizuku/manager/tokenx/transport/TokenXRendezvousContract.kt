package moe.shizuku.manager.tokenx.transport

object TokenXRendezvousContract {
    const val PROTOCOL_VERSION = 1
    const val METHOD_GET_TRANSPORT = "tokenx.getTransport"
    const val METHOD_GET_TRANSPORT_STATUS = "tokenx.getTransportStatus"
    const val EXTRA_PUBLISHED = "tokenx.published"
    const val EXTRA_BINDER_ALIVE = "tokenx.binderAlive"
    const val EXTRA_PROVIDER_PID = "tokenx.providerPid"
    const val EXTRA_PROVIDER_UID = "tokenx.providerUid"
    const val EXTRA_PROTOCOL_VERSION = "tokenx.protocolVersion"
    const val EXTRA_GENERATION = "tokenx.generation"
    const val EXTRA_BINDER = "tokenx.transportBinder"
}