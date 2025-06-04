package ru.packetdima.datascanner.scan.common.connectors

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.smithy.kotlin.runtime.content.writeToFile
import aws.smithy.kotlin.runtime.net.url.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.packetdima.datascanner.scan.common.FilesCounter
import java.io.File

val logger = KotlinLogging.logger {}

@Serializable
class ConnectorS3(
    @Serializable
    val accessKey: String,
    @Serializable
    val secretKey: String,
    @Serializable
    val endpointStr: String,
    @Serializable
    val bucketStr: String,
    @Serializable
    val regionStr: String? = null,
) : IConnector, AutoCloseable {
    private val s3Client by lazy {
        runBlocking {
            val client = S3Client.fromEnvironment {
                endpointUrl = Url.parse(endpointStr)
                region = regionStr ?: "auto"
                credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = accessKey
                    secretAccessKey = secretKey
                }
            }
            logger.info { "S3 client created" }
            client
        }
    }

    override suspend fun getFile(filePath: String): File =
        withContext(Dispatchers.Default) {
            val request = GetObjectRequest {
                bucket = bucketStr
                key = filePath
            }

            val outputFile = File.createTempFile(
                "ADS_",
                "." + filePath.substringAfterLast(".")
            )

            s3Client.getObject(request) { response ->
                response.body?.writeToFile(outputFile)
//                return@getObject outputFile
            }
            return@withContext outputFile
        }


    override suspend fun scanDirectory(
        dir: String,
        extensions: List<String>,
        fileSelected: (file: FoundedFile) -> Unit
    ): FilesCounter =
        withContext(Dispatchers.Default) {
            var filesCounter = FilesCounter()
            var contToken: String? = null
            do {
                val request = ListObjectsV2Request {
                    bucket = bucketStr
                    prefix = dir
                    continuationToken = contToken
                    delimiter = "/"
                    maxKeys = 1000
                }

                try {
                    val response = s3Client.listObjectsV2(request)

                    val folders = response.commonPrefixes?.map { it.prefix }
                    folders?.forEach {
                        if (it != null && it!= dir) {
                            filesCounter += scanDirectory(
                                dir = it,
                                extensions = extensions,
                                fileSelected = fileSelected
                            )
                        }
                    }

                    response.contents
                        ?.forEach { objectInfo ->
                            val fileSize = objectInfo.size ?: 0
                            if (fileSize > 0)
                                objectInfo.key?.let {
                                    fileSelected(
                                        FoundedFile(
                                            path = it,
                                            size = fileSize
                                        )
                                    )
                                    filesCounter.add(fileSize)
                                }
                        }

                    contToken = response.nextContinuationToken
                } catch (_: Exception) {

                }
            } while (contToken != null)
            return@withContext filesCounter
        }

    override fun close() {
        s3Client.close()
    }
}