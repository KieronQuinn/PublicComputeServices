package com.kieronquinn.app.pcs.repositories

import com.kieronquinn.app.pcs.grpc.ProtectedDownloadGrpcService
import com.kieronquinn.app.pcs.repositories.AstreaRepository.Companion.PORT
import com.kieronquinn.app.pcs.utils.extensions.fromBase64
import io.grpc.TlsServerCredentials
import io.grpc.okhttp.OkHttpServerBuilder
import java.io.ByteArrayInputStream

/**
 *  Hosts a gRPC server for downloading manifests on localhost. This is then injected into Astrea,
 *  allowing custom manifest injection.
 */
interface AstreaRepository {

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 7270 //PCS0
    }

    fun start()

}

class AstreaRepositoryImpl: AstreaRepository {

    companion object {

        // Dummy Certificate Chain (not actually verified)
        private val SERVER_PEM = """LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUROakNDQWg0Q0ZIaUNVWGJmRDlaQmtCSmRk
NlFqRk0zcFd3MS9NQTBHQ1NxR1NJYjNEUUVCQ3dVQU1GWXgKQ3pBSkJnTlZCQVlUQWtGVk1STXdF
UVlEVlFRSURBcFRiMjFsTFZOMFlYUmxNU0V3SHdZRFZRUUtEQmhKYm5SbApjbTVsZENCWGFXUm5h
WFJ6SUZCMGVTQk1kR1F4RHpBTkJnTlZCQU1NQm5SbGMzUmpZVEFlRncweU5qQXhNRGt3Ck1qSTVN
elZhRncwek5qQXhNRGN3TWpJNU16VmFNRmt4Q3pBSkJnTlZCQVlUQWtGVk1STXdFUVlEVlFRSURB
cFQKYjIxbExWTjBZWFJsTVNFd0h3WURWUVFLREJoSmJuUmxjbTVsZENCWGFXUm5hWFJ6SUZCMGVT
Qk1kR1F4RWpBUQpCZ05WQkFNTUNURXlOeTR3TGpBdU1UQ0NBU0l3RFFZSktvWklodmNOQVFFQkJR
QURnZ0VQQURDQ0FRb0NnZ0VCCkFLajQ3QlQ4b3IrRlJQaWtmMWxhUmFBSVB1QXFLRWkxWHZ6OHNI
dHZ2NG5KQWxvamdjd2x6YUg5anZMdmNPOGwKaTZmRFJSZWUwSFFzYlFtaVRZZlFFcU9HT3NmMXhC
VFgvZTJKQmlhMkpueGVUYlUxNFFlQXhUMk9GVDRQNksrTQpTSjZLVGRrdVFaWTREMG9wSkUyWVRt
RncrVzhPMzFpV1UwNE0xT21LelAxUlJUSTJaM2Z4OS82N2tEZGdNOCt0CmZ2SjdkV1pJYVVOdzZQ
QXVFY0x2ekhsMkk5SUkybHFnWWwxdW1BK3ZOMVN6akV3aUR0TUNSaVpBbkdPMXd5OTAKZ09adzhX
b1FVZWgvTDhJRFRXakxCYXJjekZGazRhVGtqbjFSSnUwYXZwV0VHb1NxcGVOb24xUkIwNmtURkZ6
TQp5T1h0SnNOZThibUZwM0ZaK3NidkFBY0NBd0VBQVRBTkJna3Foa2lHOXcwQkFRc0ZBQU9DQVFF
QVR3L1A5SXZLCmRydmY2dk8wbFl3RkJkRndLOTF3MEtkRnhSVEUyTWNSMkI2MldOaW9xNTIrckp5
R0Rta2RtcTBPWnkwM1FoeHkKY2h4SCtYdmtqRU9YZEhWMDlrYnBnaUpuVXVaVTVWK2p3OWRDc3JJ
eCtsd0ZUT2ZCcUFNTDJmekM2RmJXS1hySQp3djFUM09jZFZtb3VUU090ZjlISVhGUnZYUkZuRUxk
dHpoVlhnWDk2N0FSUE82cG1Oc1AzQnprMGYxZGJPUFJVCkdrWUdDZGpvTTJXajY5R2NrK3FWU2FM
V01FUVBYRG02MmZiM21DdGhORGRqQzY4V3JDNXZGZDE1b1pOZXViR1IKUWUwNEhyUWlpbVlqZmtL
UkUraVFjc1Zkakk5Ukp2K0FLUFRDdnFnOVhWVWNidmx0ZVZHV2hmdjdhbk45QTZxLwpqdzE1RHJM
ZU8rejI3QT09Ci0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0K""".fromBase64()

        // Dummy Private Key (not actually verified)
        private val SERVER_KEY = """LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZB
QVNDQktjd2dnU2pBZ0VBQW9JQkFRQ28rT3dVL0tLL2hVVDQKcEg5WldrV2dDRDdnS2loSXRWNzgv
TEI3YjcrSnlRSmFJNEhNSmMyaC9ZN3k3M0R2Sll1bncwVVhudEIwTEcwSgpvazJIMEJLamhqckg5
Y1FVMS8zdGlRWW10aVo4WGsyMU5lRUhnTVU5amhVK0QraXZqRWllaWszWkxrR1dPQTlLCktTUk5t
RTVoY1BsdkR0OVlsbE5PRE5UcGlzejlVVVV5Tm1kMzhmZit1NUEzWURQUHJYN3llM1ZtU0dsRGNP
ancKTGhIQzc4eDVkaVBTQ05wYW9HSmRicGdQcnpkVXM0eE1JZzdUQWtZbVFKeGp0Y012ZElEbWNQ
RnFFRkhvZnkvQwpBMDFveXdXcTNNeFJaT0drNUk1OVVTYnRHcjZWaEJxRXFxWGphSjlVUWRPcEV4
UmN6TWpsN1NiRFh2RzVoYWR4Cldmckc3d0FIQWdNQkFBRUNnZ0VBRnhYN1cxcXcrYTNCb0o4STd6
SlFOTzhyZUFFS2cvU1R4OGpXYStiMnFtS1cKT2E2RU8xb200Q3orYk42ZDlXNlJ5QmY3eHFCaUpw
MHpRSSsyUEFvSG9lUGpBQkxwK1ZjUlVIVGFMRXZTc2tkSQpJcXY3MU1TWkxqSE5ZUzlYUVRUUGJ5
bkNQUnYreGdGZXhXa2RscVJxZ1JHb1lxNExnZFQ0Q0wva0R3eHh1V0k1CjU3dmZuYldkcU1VL0Ux
SXdUNk1YUU54UkI0WUlqaXVRblR3ZWttK2RaTnJBQlppYkxUSEYvUkRBbWp1NjFvVk0KeFVET01C
dU1maXY0UVhaQ3Mvejd4ZENKZE0rY3BYbkZ3d2YraXl0cCtyb0NSMEQ2SVM0b0x1UnFSWFV0OVJN
NgpNRG04c1JyMGJ1ZEtYQ2cvZndNNUpNa2k3amVJTGVrWitiQUxCbFFvWVFLQmdRRHJWdGhjbHRq
REpadGwwb0cyClJnSjFxb0dsT0U2eGZGa3RXd1dnNlhxWXBQdmFrNFIreVdTM0YvQWhWVWdueXI2
NWhNQ0NzV0pMcFpRcjg2Q0cKOTA5b1F4OU9Zak4yK2kyUW81OXdUeEwrQnRZTGdlc0cxUHo2NVMv
WjJubWxEUmsyTnl4TEZUNkl3V0lqMmhjUQpzS0pxWXNhUk9zbDR6ZitWN0VqVjRTOFhTUUtCZ1FD
M3pvSUhMS2dldytLelJQdXB6UXRyRC9OYTJUUGViU1N3CndLYmZ0VE1iSFVQaHZJYTh1VW8zWmdK
R25GRFlBa0x6L0RxQ05vRHBlSHZFRFl1Snpwanp1N24wd3Q4b2IvNWoKT2lGQ2dhem5ZdlJlaVlI
dlZlM1QxZnVrZ2FJejhYdU5kYzcwTXNHVEFRTTNvU282QkFoSTVjZTZqRmc0VWozYQpGclR5bitY
TXp3S0JnUUNDK09VV3Vsak9Xa3FlbzBYUEpDMVFRT0ZBQ3hNNGthU3Jxa0Y2cVJXeWgvY21VUHpt
Cjlyd1hiYm9WUXZvU016SnIydWFzbFgzSDdkR0ZtR09aV1YrVy9ld1pXbXViNW1XZlhvQm9KNG5C
V0JxZGN1TlMKL1F3QnNiVXN2L3I5RVVvYnN4N3lkbE5FRnFQQW9pbzkvcCtWSjMyczY1T2VxTDU2
T0hMY25TTHhDUUtCZ0Q4Ngo4aTRucFpvWHh0Zm14akJPa2p2OVc3a0grVGp1RU80aENBYnpIYWFa
TmEzbEhmQzBTUnl2b2Q3S2pXRVJ2aGlTCnowbldHQmk0MHRMSjJoUEpGNExaTklwSHMxOEV6OTB3
dFJwYzQ2OGhzbkVIR3NUTHFhbnk5Y05NdVJEblpKcHcKU1labUk0TS9tT3k5SzNxVHdvblpTaEVa
a0l1bmR4R2NPQmt5K21tdkFvR0FjaWFIOEx2SXM2U2ZwWHR4b205aApVVXY5U2NTbSthMVBsN2VM
V20vUEJFT0VkeU5TM1J6YXYvSVNSbVlzM1RKWVFMM3lVbkJNcWlKb1dka0M0US9xCjE4Q1JDR1dt
d3IxU1ZHU3JHUStGUDhDSlg1Zkc4YjhKQ2NuSEVXNUY3VEV1Y2tVK0owNGkyRVVNZy9sS2RKZisK
TWdnT0FtKzkzU01GYzVGVzdKYjlmQjQ9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K""".fromBase64()

        private const val MAX_RESPONSE_SIZE_IN_BYTES: Int = 32 * 1024 * 1024 // Max download size: 32MB
    }

    private val server by lazy {
        val key = ByteArrayInputStream(SERVER_KEY)
        val pem = ByteArrayInputStream(SERVER_PEM)
        val credentials = TlsServerCredentials.newBuilder()
            .keyManager(pem, key)
            .build()
        key.close()
        pem.close()
        OkHttpServerBuilder.forPort(PORT, credentials)
            .maxInboundMessageSize(MAX_RESPONSE_SIZE_IN_BYTES)
            .addService(ProtectedDownloadGrpcService())
            .build()
    }

    override fun start() {
        try {
            server.start()
        }catch (e: IllegalStateException) {
            // Already started
        }
    }

}