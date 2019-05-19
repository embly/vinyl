package app

import com.apple.foundationdb.record.metadata.Key
import com.apple.foundationdb.record.provider.foundationdb.FDBDatabase
import com.apple.foundationdb.record.provider.foundationdb.FDBDatabaseFactory
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore
import com.apple.foundationdb.record.provider.foundationdb.FDBStoredRecord
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpace
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpaceDirectory
import com.apple.foundationdb.record.provider.foundationdb.keyspace.KeySpacePath
import com.apple.foundationdb.record.RecordMetaData
import com.apple.foundationdb.record.RecordMetaDataBuilder
import com.apple.foundationdb.tuple.Tuple
import com.google.protobuf.Message
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun getGreeting(): String {
    val words = mutableListOf<String>()
    words.add("Hello,")
    words.add("world!")

    return words.joinToString(separator = " ")
}

fun main(args: Array<String>) {
    val server = ServerSocket(9999)
    val db = FDBDatabaseFactory.instance().getDatabase()
    println("Server is running on port ${server.localPort}")

    while (true) {
        val client = server.accept()
        println("Client connected: ${client.inetAddress.hostAddress}")

        // Run client in it's own thread.
        thread { ClientHandler(client, db).run() }
    }


    // db.run(fun (context) {
    //     val recordStore = recordStoreProvider(context)

    //     recordStore.saveRecord(RecordLayerDemoProto.Value.newBuilder()
    //         .setKey("foo")
    //         .setValue("asdfasdf")
    //         .setIntValue(4)
    //         .build())
    // })

    // val storedRecord = db.run(fun (context): FDBStoredRecord<Message>? {
    //     println("Within context")
    //     return recordStoreProvider(context).loadRecord(Tuple.from("foo"))
    // })

    // println(storedRecord)
    // if (storedRecord != null) {
    //     println(storedRecord.getRecord())
    // }

}

class ClientHandler(client: Socket, db: FDBDatabase) {
    private val client: Socket = client
    private val db: FDBDatabase = db
    private val reader: Scanner = Scanner(client.getInputStream())
    private val writer: OutputStream = client.getOutputStream()
    private var running: Boolean = false
    private var database: String? = null
    private var store: FDBRecordStore? = null
    private var path: KeySpacePath? = null
    private var metaDataBuilder: RecordMetaDataBuilder? = null

    fun run() {
        running = true
        // Welcome message
        write("Welcome to the server!\n" +
                "To Exit, write: 'exit'.")
        while (running) {
            try {
                val text = reader.nextLine()
                if (text == "exit"){
                    shutdown()
                    continue
                }
                processCommand(text)
            } catch (ex: Exception) {
                println(ex)
                shutdown()
            } finally {

            }
        }

        shutdown()
    }
    private fun processCommand(text: String) {
        val parts = text.split(" ")
        if (parts.size == 0) {
            return
        }
        println(parts)
        val command = parts[0]
        if (command == "setup") {
            this.setup(parts[1])
            write("Database set to ${parts[1]}")
            return
        }
        if (database == null) {
            write("Database has not been set. Set with \"SETUP name\"")
            return
        }
        if (command == "get") {
            if (parts.size < 2) {
                write("ERR wrong number of arguments for 'get' command")
                return
            }
            write("${this.get(parts[1])}")
        } else if (command == "set") {
            if (parts.size < 3) {
                write("ERR wrong number of arguments for 'set' command")
                return
            }
            this.set(parts[1], parts[2])
            write("OK")
        } else {
            write("Command \"${command}\" not found")
        }
    }
    private fun write(text: String) {
        writer.write((text + '\n').toByteArray(Charset.defaultCharset()))
    }

    private fun get(key: String): String {
        val storedRecord = this.db.run(fun (context): FDBStoredRecord<Message>? {
            return this.provider(context).loadRecord(Tuple.from(key))
        })
        if (storedRecord != null) {
            var value = RecordLayerDemoProto.Value.newBuilder().mergeFrom(storedRecord.getRecord()).build()
            return value.getValue()
        }
        return ""
    }

    private fun set(key: String, value: String) {
        this.db.run(fun (context) {
            this.provider(context)
                .saveRecord(RecordLayerDemoProto.Value.newBuilder()
                .setKey(key)
                .setValue(value)
                .build())
        })
    }

    private fun setup(name: String) {
        this.database = name
        val keySpace = KeySpace(KeySpaceDirectory(name, KeySpaceDirectory.KeyType.STRING, name))

        this.path = keySpace.path(name)
        val mdb = RecordMetaData.newBuilder()
                        .setRecords(RecordLayerDemoProto.getDescriptor())

        mdb.getRecordType("Value")
            .setPrimaryKey(Key.Expressions.field("key"))

        this.metaDataBuilder = mdb
    }


    private fun provider(context: FDBRecordContext): FDBRecordStore {
        return FDBRecordStore.newBuilder()
            .setMetaDataProvider(this.metaDataBuilder)
            .setContext(context)
            .setKeySpacePath(this.path)
            .createOrOpen()
    }

    private fun shutdown() {
        running = false
        client.close()
        println("${client.inetAddress.hostAddress} closed the connection")
        exitProcess(0)
    }

}
